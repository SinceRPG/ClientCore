package net.danh.clientcore.mob;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.hook.plugin.MythicMobsHook;
import net.danh.clientcore.luck.LuckService;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.storage.StorageService;
import net.danh.clientcore.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientMobService implements Listener {
    private static final double VISUAL_ENTITY_RADIUS_SQUARED = 9.0D;
    private static final EnumSet<EntityType> VISUAL_ENTITY_TYPES = EnumSet.of(
            EntityType.ARMOR_STAND,
            EntityType.TEXT_DISPLAY,
            EntityType.ITEM_DISPLAY,
            EntityType.BLOCK_DISPLAY,
            EntityType.INTERACTION,
            EntityType.SLIME,
            EntityType.MAGMA_CUBE
    );

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final FoliaScheduler scheduler;
    private final HookRegistry hooks;
    private final ClientPacketService packets;
    private final LuckService luck;
    private final ConditionEvaluator conditions;
    private final NamespacedKey ownerKey;
    private final NamespacedKey spawnKey;
    private final SpawnPointStore spawns;
    private final Map<UUID, UUID> owners = new ConcurrentHashMap<>();
    private final Map<UUID, String> ownerSpawns = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> clientEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> entityIds = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> packetEntityOwners = new ConcurrentHashMap<>();
    private final ThreadLocal<PendingSpawn> pendingSpawn = new ThreadLocal<>();
    private final Random random = new Random();
    private final AtomicLong spawnTick = new AtomicLong();
    private List<MobRule> rules = List.of();
    private boolean enabled;
    private ScheduledTask spawnTask;

    public ClientMobService(Plugin plugin, ConfigManager configManager, FoliaScheduler scheduler, HookRegistry hooks, ClientPacketService packets, LuckService luck, StorageService storage) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
        this.hooks = hooks;
        this.packets = packets;
        this.luck = luck;
        this.conditions = new ConditionEvaluator(hooks);
        this.ownerKey = new NamespacedKey(plugin, "client_mob_owner");
        this.spawnKey = new NamespacedKey(plugin, "client_mob_spawn");
        this.spawns = new SpawnPointStore(plugin, configManager, storage);
        this.spawns.load();
    }

    public void reload() {
        this.enabled = configManager.getMobs().getBoolean("client-mobs.enabled", true);
        this.rules = new MobRuleLoader(plugin, configManager).load();
        if (spawnTask != null) spawnTask.cancel();
        spawnTask = scheduler.globalTimer(20L, 20L, task -> tickSpawns());
    }

    public void shutdown() {
        for (UUID uuid : owners.keySet()) {
            Entity entity = clientEntities.get(uuid);
            if (entity != null) {
                if (plugin.isEnabled()) {
                    entity.getScheduler().execute(plugin, entity::remove, null, 1L);
                } else {
                    try {
                        entity.remove();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        owners.clear();
        ownerSpawns.clear();
        clientEntities.clear();
        entityIds.clear();
        packetEntityOwners.clear();
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
    }

    public int ruleCount() {
        return rules.size();
    }

    public List<String> ruleIds() {
        return rules.stream().map(MobRule::id).toList();
    }

    public List<String> mythicMobIds() {
        return hooks.mythicMobIds();
    }

    public List<String> spawnIds() {
        return spawns.all().stream().map(SpawnPoint::id).toList();
    }

    public Optional<Entity> spawnFor(Player viewer, Location location) {
        return spawnFor(viewer, location, "", 1.0D, "");
    }

    public Optional<Entity> spawnFor(Player viewer, Location location, String ruleId, double level, String spawnId) {
        if (!enabled) return Optional.empty();

        Optional<MobMatch> optional = rules.stream()
                .filter(rule -> ruleId == null || ruleId.isBlank() || rule.id().equalsIgnoreCase(ruleId) || rule.variants().stream().anyMatch(variant -> variant.mythicMobId().equalsIgnoreCase(ruleId)))
                .map(rule -> new MobMatch(rule, conditions.evaluate(viewer, rule.condition(), rule.conditions())))
                .filter(match -> match.evaluation().passed())
                .max(Comparator.comparingInt(match -> match.rule().priority()));

        if (optional.isEmpty()) return Optional.empty();

        MobRule rule = optional.get().rule();
        MobVariant variant = chooseVariant(viewer, rule, optional.get().evaluation().passedOptionalIds());
        World world = location.getWorld();
        if (world == null) return Optional.empty();

        Entity entity = spawnOwned(viewer, location, spawnId, () -> hooks.mythicMob(variant.mythicMobId(), location, level)
                .orElseGet(() -> world.spawnEntity(location, variant.fallbackEntity())));

        claimEntity(entity, viewer, spawnId, entity instanceof ArmorStand);

        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            applyStats(living, variant);
        }
        if (entity instanceof Mob mob) {
            mob.setTarget(viewer);
        }
        enforceOwnerTarget(entity, viewer);
        claimNearbyVisualEntities(entity, viewer, spawnId);
        return Optional.of(entity);
    }

    public Optional<Entity> spawnMythicFor(Player viewer, Location location, String mythicMobId, double level) {
        if (!enabled || mythicMobId == null || mythicMobId.isBlank()) return Optional.empty();
        Entity entity = spawnOwned(viewer, location, "", () -> hooks.mythicMob(mythicMobId, location, level).orElse(null));
        if (entity == null) return Optional.empty();

        claimEntity(entity, viewer, "", entity instanceof ArmorStand);
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
        }
        if (entity instanceof Mob mob) {
            mob.setTarget(viewer);
        }
        enforceOwnerTarget(entity, viewer);
        claimNearbyVisualEntities(entity, viewer, "");
        return Optional.of(entity);
    }

    public SpawnPoint setSpawn(String id, Location location) {
        return spawns.set(id, location);
    }

    public boolean deleteSpawn(String id) {
        return spawns.delete(id);
    }

    public Optional<SpawnPoint> spawn(String id) {
        return spawns.get(id);
    }

    public List<SpawnPoint> spawnList() {
        return List.copyOf(spawns.all());
    }

    public void saveSpawns() {
        spawns.save();
    }

    public Map<UUID, UUID> getOwners() {
        return owners;
    }

    public Map<UUID, Entity> getClientEntities() {
        return clientEntities;
    }

    public Map<Integer, UUID> getPacketEntityOwners() {
        return packetEntityOwners;
    }

    public Optional<Player> ownerPlayer(Entity entity) {
        UUID ownerId = ownerOf(entity);
        return ownerId == null ? Optional.empty() : Optional.ofNullable(Bukkit.getPlayer(ownerId));
    }

    public void claimPacketEntityIds(Collection<Integer> entityIds, Player viewer) {
        if (entityIds == null || entityIds.isEmpty()) return;
        for (Integer entityId : entityIds) {
            if (entityId == null || entityId < 0) continue;
            packetEntityOwners.put(entityId, viewer.getUniqueId());
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                    packets.destroyEntity(online, entityId);
                }
            }
        }
    }

    public void claimNearbyVisualEntities(Entity source, Player viewer) {
        claimNearbyVisualEntities(source, viewer, ownerSpawns.get(source.getUniqueId()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (Entity entity : clientEntities.values()) {
            player.hideEntity(plugin, entity);
            packets.destroyEntity(player, entity.getEntityId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        for (Map.Entry<UUID, UUID> entry : owners.entrySet()) {
            if (entry.getValue().equals(viewerId)) {
                Entity entity = clientEntities.get(entry.getKey());
                if (entity != null) entity.getScheduler().execute(plugin, entity::remove, null, 1L);
            }
        }
        owners.entrySet().removeIf(entry -> entry.getValue().equals(viewerId));
        ownerSpawns.keySet().removeIf(uuid -> !owners.containsKey(uuid));
        clientEntities.keySet().removeIf(uuid -> !owners.containsKey(uuid));
        entityIds.keySet().removeIf(uuid -> !owners.containsKey(uuid));
        packetEntityOwners.entrySet().removeIf(entry -> entry.getValue().equals(viewerId));
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        UUID owner = ownerOf(event.getEntity());
        if (owner == null) return;
        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (!(event.getTarget() instanceof Player player) || !player.getUniqueId().equals(owner)) {
            event.setCancelled(true);
            if (ownerPlayer != null) {
                enforceOwnerTarget(event.getEntity(), ownerPlayer);
            }
        } else if (ownerPlayer != null) {
            enforceOwnerTarget(event.getEntity(), ownerPlayer);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        UUID victimOwner = ownerOf(event.getEntity());
        if (victimOwner != null && event.getDamager() instanceof Player player && !player.getUniqueId().equals(victimOwner)) {
            event.setCancelled(true);
            return;
        }
        UUID damagerOwner = ownerOf(event.getDamager());
        if (damagerOwner != null && event.getEntity() instanceof Player player && !player.getUniqueId().equals(damagerOwner)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (ownerOf(event.getEntity()) != null) forget(event.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        PendingSpawn pending = pendingSpawn.get();
        if (pending != null && pending.matches(entity)) {
            claimEntity(entity, pending.owner(), pending.spawnId(), false);
            return;
        }

        Entity nearestOwned = nearestOwnedEntity(entity);
        if (nearestOwned != null) {
            UUID ownerId = owners.get(nearestOwned.getUniqueId());
            Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
            if (owner != null && (VISUAL_ENTITY_TYPES.contains(entity.getType()) || MythicMobsHook.isMythicMob(entity))) {
                claimEntity(entity, owner, ownerSpawns.get(nearestOwned.getUniqueId()), false);
            }
        }

        if (!configManager.getMain().getBoolean("hooks.mythicmobs", false)) return;

        entity.getScheduler().execute(plugin, () -> {
            if (!entity.isValid()) return;
            UUID parentId = MythicMobsHook.getParentUUID(entity);
            if (parentId != null && owners.containsKey(parentId)) {
                UUID ownerId = owners.get(parentId);
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null) {
                    claimEntity(entity, owner, ownerSpawns.get(parentId), false);
                }
            }
        }, null, 2L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMythicMobSpawn(MythicMobSpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) return;

        UUID ownerId = ownerOf(entity);
        String spawnId = null;
        PendingSpawn pending = pendingSpawn.get();
        if (pending != null && pending.matches(entity)) {
            ownerId = pending.owner().getUniqueId();
            spawnId = pending.spawnId();
        }
        if (ownerId == null) {
            UUID parentId = MythicMobsHook.getParentUUID(entity);
            if (parentId != null) {
                ownerId = owners.get(parentId);
                spawnId = ownerSpawns.get(parentId);
            }
        }
        if (ownerId == null) {
            Entity nearestOwned = nearestOwnedEntity(entity);
            if (nearestOwned != null) {
                ownerId = owners.get(nearestOwned.getUniqueId());
                spawnId = ownerSpawns.get(nearestOwned.getUniqueId());
            }
        }
        if (ownerId == null) return;

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;
        claimEntity(entity, owner, spawnId, false);
        enforceOwnerTarget(entity, owner);
        claimNearbyVisualEntities(entity, owner, spawnId);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityMount(org.bukkit.event.entity.EntityMountEvent event) {
        Entity mount = event.getMount();
        Entity passenger = event.getEntity();

        UUID ownerId = ownerOf(passenger);
        if (ownerId != null) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null) {
                claimEntity(mount, owner, ownerSpawns.get(passenger.getUniqueId()), false);
                enforceOwnerTarget(mount, owner);
            }
        }
    }

    private void applyStats(LivingEntity entity, MobVariant variant) {
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(variant.health());
        if (maxHealth != null) entity.setHealth(Math.min(variant.health(), maxHealth.getValue()));
        AttributeInstance attack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(variant.damage());
    }

    private MobVariant chooseVariant(Player player, MobRule rule, Set<String> passedConditionIds) {
        List<MobVariant> eligible = rule.variants().stream()
                .filter(variant -> variant.requiredConditionIds().isEmpty() || passedConditionIds.containsAll(variant.requiredConditionIds()))
                .toList();
        if (eligible.isEmpty())
            eligible = rule.variants().stream().filter(variant -> variant.requiredConditionIds().isEmpty()).toList();
        if (eligible.isEmpty()) eligible = rule.variants();
        if (eligible.size() == 1) return eligible.getFirst();

        double playerLuck = luck.luck(player);
        double total = 0.0D;
        for (MobVariant variant : eligible) {
            total += adjustedWeight(variant.weight(), variant.rare(), variant.luckMultiplier(), playerLuck);
        }
        if (total <= 0.0D) return eligible.getFirst();

        double roll = random.nextDouble(total);
        double cursor = 0.0D;
        for (MobVariant variant : eligible) {
            cursor += adjustedWeight(variant.weight(), variant.rare(), variant.luckMultiplier(), playerLuck);
            if (roll <= cursor) return variant;
        }
        return eligible.getLast();
    }

    private double adjustedWeight(double base, boolean rare, double multiplier, double playerLuck) {
        if (!rare || playerLuck <= 0.0D) return base;
        double maxBonus = Math.max(0.0D, configManager.getMain().getDouble("luck.max-rare-weight-bonus-percent", 300.0D)) / 100.0D;
        double bonus = Math.min(maxBonus, playerLuck * Math.max(0.0D, multiplier) / 100.0D);
        return base * (1.0D + bonus);
    }

    private void tickSpawns() {
        if (!enabled) return;
        for (UUID entityId : owners.keySet()) {
            Entity entity = clientEntities.get(entityId);
            if (entity == null) {
                forget(entityId);
            } else {
                UUID ownerId = owners.get(entityId);
                entity.getScheduler().execute(plugin, () -> validateOwnedEntity(entityId, entity, ownerId), null, 1L);
            }
        }

        long tick = spawnTick.incrementAndGet() * 20L;
        for (SpawnPoint point : spawns.all()) {
            if (!point.enabled() || tick % point.intervalTicks() != 0) continue;
            Location center = point.location();
            if (center == null || center.getWorld() == null) continue;
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().execute(plugin, () -> tickSpawnPointForPlayer(point, player), null, 1L);
            }
        }
    }

    private void validateOwnedEntity(UUID entityId, Entity entity, UUID ownerId) {
        if (!entity.isValid()) {
            forget(entityId);
            return;
        }
        Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        if (owner != null) {
            enforceOwnerTarget(entity, owner);
        }
    }

    private void tickSpawnPointForPlayer(SpawnPoint point, Player player) {
        if (!enabled) return;
        Location center = point.location();
        if (center == null || center.getWorld() == null) return;
        if (player.getWorld() != center.getWorld() || player.getLocation().distanceSquared(center) > point.activationRange() * point.activationRange())
            return;

        int alive = aliveFor(player.getUniqueId(), point.id());
        int canSpawn = Math.min(point.batchSize(), Math.min(point.amount(), point.maxAlive() - alive));
        for (int i = 0; i < canSpawn; i++) {
            Location spawnLocation = randomLocation(center, point.radius());
            scheduler.region(spawnLocation, () -> spawnFor(player, spawnLocation, point.rule(), point.level(), point.id()));
        }
    }

    private int aliveFor(UUID owner, String spawnId) {
        int count = 0;
        for (Map.Entry<UUID, UUID> entry : owners.entrySet()) {
            if (!entry.getValue().equals(owner) || !spawnId.equals(ownerSpawns.get(entry.getKey()))) continue;
            count++;
        }
        return count;
    }

    private void forget(UUID entityId) {
        owners.remove(entityId);
        ownerSpawns.remove(entityId);
        clientEntities.remove(entityId);
        Integer packetEntityId = entityIds.remove(entityId);
        if (packetEntityId != null) packetEntityOwners.remove(packetEntityId);
    }

    private void claimEntity(Entity entity, Player viewer, String spawnId, boolean hideArmorStandCarrier) {
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, viewer.getUniqueId().toString());
        if (spawnId != null && !spawnId.isBlank()) {
            entity.getPersistentDataContainer().set(spawnKey, PersistentDataType.STRING, spawnId);
            ownerSpawns.put(entity.getUniqueId(), spawnId);
        }

        owners.put(entity.getUniqueId(), viewer.getUniqueId());
        clientEntities.put(entity.getUniqueId(), entity);
        entityIds.put(entity.getUniqueId(), entity.getEntityId());
        claimPacketEntityIds(List.of(entity.getEntityId()), viewer);
        if (hideArmorStandCarrier && entity instanceof ArmorStand armorStand) {
            armorStand.setVisible(false);
            armorStand.setCollidable(false);
            armorStand.setSilent(true);
        }
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
        }
        if (entity instanceof Mob mob) {
            mob.setTarget(viewer);
        }
        enforceOwnerTarget(entity, viewer);
        hideFromOthers(entity, viewer);
    }

    private Entity spawnOwned(Player viewer, Location location, String spawnId, java.util.function.Supplier<Entity> supplier) {
        PendingSpawn previous = pendingSpawn.get();
        pendingSpawn.set(new PendingSpawn(viewer, location, spawnId));
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                pendingSpawn.remove();
            } else {
                pendingSpawn.set(previous);
            }
        }
    }

    private void enforceOwnerTarget(Entity entity, Player owner) {
        if (entity instanceof Mob mob && mob.getTarget() != owner) {
            mob.setTarget(owner);
        }
        if (configManager.getMain().getBoolean("hooks.mythicmobs", false)) {
            MythicMobsHook.setOwnerTarget(entity, owner);
        }
    }

    private void claimNearbyVisualEntities(Entity source, Player viewer, String spawnId) {
        for (Entity nearby : source.getNearbyEntities(3.0D, 3.0D, 3.0D)) {
            if (nearby.getUniqueId().equals(source.getUniqueId())) continue;
            if (!VISUAL_ENTITY_TYPES.contains(nearby.getType())) continue;
            claimEntity(nearby, viewer, spawnId, false);
        }
    }

    private Entity nearestOwnedEntity(Entity entity) {
        Entity nearest = null;
        double bestDistance = VISUAL_ENTITY_RADIUS_SQUARED;
        for (Entity owned : clientEntities.values()) {
            if (owned == null || !owned.isValid() || owned.getWorld() != entity.getWorld()) continue;
            double distance = owned.getLocation().distanceSquared(entity.getLocation());
            if (distance <= bestDistance) {
                nearest = owned;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    private Location randomLocation(Location center, double radius) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        return center.clone().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
    }

    private void hideFromOthers(Entity entity, Player viewer) {
        entity.setVisibleByDefault(false);
        viewer.showEntity(plugin, entity);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                online.hideEntity(plugin, entity);
                packets.destroyEntity(online, entity.getEntityId());
            }
        }
    }

    private UUID ownerOf(Entity entity) {
        String value = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (value == null) return owners.get(entity.getUniqueId());
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record MobMatch(MobRule rule, ConditionEvaluator.Evaluation evaluation) {
    }

    private record PendingSpawn(Player owner, Location location, String spawnId) {
        private boolean matches(Entity entity) {
            return entity.getWorld() == location.getWorld() && entity.getLocation().distanceSquared(location) <= 16.0D;
        }
    }
}

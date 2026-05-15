package net.danh.clientcore.mob;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.HookRegistry;
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
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientMobService implements Listener {
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
                entity.getScheduler().execute(plugin, entity::remove, null, 1L);
            }
        }
        owners.clear();
        ownerSpawns.clear();
        clientEntities.clear();
        entityIds.clear();
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

        Entity entity = hooks.mythicMob(variant.mythicMobId(), location, level)
                .orElseGet(() -> world.spawnEntity(location, variant.fallbackEntity()));
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, viewer.getUniqueId().toString());
        if (spawnId != null && !spawnId.isBlank()) {
            entity.getPersistentDataContainer().set(spawnKey, PersistentDataType.STRING, spawnId);
        }

        owners.put(entity.getUniqueId(), viewer.getUniqueId());
        clientEntities.put(entity.getUniqueId(), entity);
        entityIds.put(entity.getUniqueId(), entity.getEntityId());
        if (spawnId != null && !spawnId.isBlank()) ownerSpawns.put(entity.getUniqueId(), spawnId);

        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            applyStats(living, variant);
        }
        if (entity instanceof Mob mob) {
            mob.setTarget(viewer);
        }
        hideFromOthers(entity, viewer);
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (int entityId : entityIds.values()) packets.destroyEntity(event.getPlayer(), entityId);
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
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        UUID owner = ownerOf(event.getEntity());
        if (owner == null) return;
        if (!(event.getTarget() instanceof Player player) || !player.getUniqueId().equals(owner)) {
            event.setCancelled(true);
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
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Damageable damageable && ownerOf(event.getEntity()) != null && damageable.isDead()) {
            forget(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (ownerOf(event.getEntity()) != null) forget(event.getEntity().getUniqueId());
    }

    private void applyStats(LivingEntity entity, MobVariant variant) {
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(variant.health());
        entity.setHealth(Math.min(variant.health(), entity.getMaxHealth()));
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
        entityIds.remove(entityId);
    }

    private Location randomLocation(Location center, double radius) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        return center.clone().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
    }

    private void hideFromOthers(Entity entity, Player viewer) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                online.hideEntity(plugin, entity);
                packets.destroyEntity(online, entity.getEntityId());
            } else {
                online.showEntity(plugin, entity);
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
}
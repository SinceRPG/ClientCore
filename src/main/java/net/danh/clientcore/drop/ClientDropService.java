package net.danh.clientcore.drop;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.condition.CooldownRule;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.item.ConfigItemBuilder;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.storage.CooldownManager;
import net.danh.clientcore.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientDropService implements Listener {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final FoliaScheduler scheduler;
    private final HookRegistry hooks;
    private final ClientPacketService packets;
    private final ConditionEvaluator conditions;
    private final ConfigItemBuilder itemBuilder;
    private final CooldownManager cooldownManager;
    private final Map<UUID, Map<String, Entity>> activeDrops = new ConcurrentHashMap<>();
    private List<DropRule> rules = List.of();
    private boolean enabled;
    private int refreshRadius;
    private ScheduledTask refreshTask;

    public ClientDropService(Plugin plugin, ConfigManager configManager, FoliaScheduler scheduler, HookRegistry hooks, ClientPacketService packets, ConditionEvaluator conditions, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
        this.hooks = hooks;
        this.packets = packets;
        this.conditions = conditions;
        this.cooldownManager = cooldownManager;
        this.itemBuilder = new ConfigItemBuilder(plugin, hooks);
    }

    public void reload() {
        this.enabled = configManager.getDrops().getBoolean("client-drops.enabled", true);
        // Bổ sung bán kính render cho drops
        this.refreshRadius = Math.max(1, configManager.getDrops().getInt("client-drops.refresh-radius", 15));
        this.rules = new DropRuleLoader(plugin, configManager.getDrops()).load();

        if (refreshTask != null) refreshTask.cancel();

        long period = configManager.getDrops().getLong("client-drops.refresh-period-ticks", 40L);
        refreshTask = scheduler.globalTimer(20L, period, task -> {
            if (!enabled) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().execute(plugin, () -> refreshFor(player), null, 1L);
            }
        });
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        for (Map<String, Entity> playerDrops : activeDrops.values()) {
            for (Entity entity : playerDrops.values()) {
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
        activeDrops.clear();
    }

    private void refreshFor(Player player) {
        if (!player.isOnline()) return;
        Map<String, Entity> spawned = activeDrops.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());

        for (DropRule rule : rules) {
            Location loc = rule.location();
            // An toàn cho Folia: Bỏ qua nếu khác World hoặc Chunk chưa được tải
            if (loc.getWorld() != player.getWorld() || !loc.isChunkLoaded()) continue;

            double dist = player.getLocation().distanceSquared(loc);
            boolean inRange = dist <= (refreshRadius * refreshRadius);

            boolean isOnCooldown = cooldownManager.isOnCooldown(player.getUniqueId(), "drop", rule.id());
            boolean passedConditions = conditions.evaluate(player, rule.condition(), rule.conditions()).passed();
            boolean shouldSpawn = inRange && passedConditions && !isOnCooldown;

            Entity currentDrop = spawned.get(rule.id());
            // Dọn dẹp Ghost Entity nếu nó đã bị server xóa (despawn/clearlag)
            if (currentDrop != null && (!currentDrop.isValid() || Bukkit.getEntity(currentDrop.getUniqueId()) == null)) {
                spawned.remove(rule.id());
                currentDrop = null;
            }

            boolean exists = currentDrop != null;

            if (shouldSpawn && !exists) {
                scheduler.region(loc, () -> {
                    if (!loc.isChunkLoaded()) return;
                    ItemStack itemStack = itemBuilder.build(player, rule.itemConfig());
                    if (itemStack.isEmpty()) return;

                    Item item = loc.getWorld().dropItem(loc, itemStack);
                    item.setPickupDelay(0);
                    item.setGravity(false);
                    item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    item.setInvulnerable(true);

                    hideFromOthers(item, player);
                    spawned.put(rule.id(), item);
                });
            } else if (!shouldSpawn && exists) {
                Entity item = spawned.remove(rule.id());
                if (item != null) item.getScheduler().execute(plugin, item::remove, null, 1L);
            }
        }
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

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Map<String, Entity> drops = activeDrops.get(player.getUniqueId());
        if (drops == null) return;

        for (Map.Entry<String, Entity> entry : drops.entrySet()) {
            if (entry.getValue().equals(event.getItem())) {
                event.setCancelled(true);
                String ruleId = entry.getKey();
                DropRule rule = getRule(ruleId);

                if (rule == null || cooldownManager.isOnCooldown(player.getUniqueId(), "drop", ruleId)) return;

                player.getInventory().addItem(event.getItem().getItemStack());

                long duration = calculateCooldown(player, rule.cooldowns());
                if (duration > 0) {
                    cooldownManager.setCooldown(player.getUniqueId(), "drop", ruleId, System.currentTimeMillis() + (duration * 50L));
                }

                event.getItem().remove();
                drops.remove(ruleId);
                break;
            }
        }
    }

    private long calculateCooldown(Player player, List<CooldownRule> cooldownRules) {
        for (CooldownRule cdRule : cooldownRules) {
            if (conditions.evaluate(player, cdRule.condition(), cdRule.conditions()).passed()) {
                return cdRule.durationTicks();
            }
        }
        return 0L;
    }

    private DropRule getRule(String id) {
        return rules.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (Map<String, Entity> drops : activeDrops.values()) {
            for (Entity entity : drops.values()) {
                player.hideEntity(plugin, entity);
                packets.destroyEntity(player, entity.getEntityId());
            }
        }
        scheduler.regionLater(player.getLocation(), 20L, task -> refreshFor(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Map<String, Entity> drops = activeDrops.remove(event.getPlayer().getUniqueId());
        if (drops != null) {
            for (Entity drop : drops.values()) {
                drop.getScheduler().execute(plugin, drop::remove, null, 1L);
            }
        }
    }
}
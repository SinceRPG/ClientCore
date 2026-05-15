package net.danh.clientcore.chest;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.condition.CooldownRule;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.item.ConfigItemBuilder;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.storage.CooldownManager;
import net.danh.clientcore.util.FoliaScheduler;
import net.danh.clientcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientLootChestService implements Listener {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final FoliaScheduler scheduler;
    private final HookRegistry hooks;
    private final ClientPacketService packets;
    private final ConditionEvaluator conditions;
    private final ConfigItemBuilder itemBuilder;
    private final CooldownManager cooldownManager;
    private final Map<UUID, Map<String, Location>> visibleChests = new ConcurrentHashMap<>();
    private List<LootChestRule> rules = List.of();
    private boolean enabled;
    private int refreshRadius;
    private ScheduledTask refreshTask;

    public ClientLootChestService(Plugin plugin, ConfigManager configManager, FoliaScheduler scheduler, HookRegistry hooks, ClientPacketService packets, ConditionEvaluator conditions, CooldownManager cooldownManager) {
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
        this.enabled = configManager.getChests().getBoolean("client-loot-chests.enabled", true);
        this.refreshRadius = Math.max(1, configManager.getChests().getInt("client-loot-chests.refresh-radius", 10));
        this.rules = new LootChestRuleLoader(plugin, configManager.getChests()).load();

        if (refreshTask != null) refreshTask.cancel();

        long period = configManager.getChests().getLong("client-loot-chests.refresh-period-ticks", 40L);
        refreshTask = scheduler.globalTimer(20L, period, task -> {
            if (!enabled) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().execute(plugin, () -> refreshAround(player), null, 1L);
            }
        });
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        visibleChests.clear();
    }

    private void refreshAround(Player player) {
        if (!player.isOnline()) return;
        Map<String, Location> visible = visibleChests.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());

        for (LootChestRule rule : rules) {
            Location loc = rule.location();
            if (loc.getWorld() != player.getWorld()) continue;

            double dist = player.getLocation().distanceSquared(loc);
            boolean inRange = dist <= (refreshRadius * refreshRadius);
            boolean isOnCooldown = cooldownManager.isOnCooldown(player.getUniqueId(), "chest", rule.id());
            boolean passedConditions = conditions.evaluate(player, rule.condition(), rule.conditions()).passed();
            boolean shouldBeVisible = inRange && passedConditions && !isOnCooldown;
            boolean isVisible = visible.containsKey(rule.id());

            if (shouldBeVisible && !isVisible) {
                packets.sendBlock(player, loc, rule.displayBlock());
                visible.put(rule.id(), loc);
            } else if (!shouldBeVisible && isVisible) {
                visible.remove(rule.id());
                packets.sendBlock(player, loc, loc.getWorld().getBlockAt(loc).getBlockData());
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Location clicked = event.getClickedBlock().getLocation();
        Player player = event.getPlayer();
        Map<String, Location> visible = visibleChests.get(player.getUniqueId());
        if (visible == null) return;

        for (LootChestRule rule : rules) {
            if (visible.containsKey(rule.id()) && rule.location().equals(clicked)) {
                event.setCancelled(true);

                if (cooldownManager.isOnCooldown(player.getUniqueId(), "chest", rule.id())) {
                    Text.sendConfig(player, configManager, "commands.chest-cooldown");
                    return;
                }

                long duration = calculateCooldown(player, rule.cooldowns());
                if (duration > 0) {
                    cooldownManager.setCooldown(player.getUniqueId(), "chest", rule.id(), System.currentTimeMillis() + (duration * 50L));
                }

                visible.remove(rule.id());
                packets.sendBlock(player, clicked, clicked.getWorld().getBlockAt(clicked).getBlockData());

                Inventory inv = Bukkit.createInventory(null, 27, Text.mm(rule.guiTitle()));
                List<ItemStack> items = itemBuilder.buildAll(player, rule.drops());
                for (ItemStack item : items) {
                    inv.addItem(item);
                }
                player.openInventory(inv);
                return;
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

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled || event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ())
            return;
        refreshAround(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduler.regionLater(event.getPlayer().getLocation(), 5L, task -> refreshAround(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        visibleChests.remove(event.getPlayer().getUniqueId());
    }
}
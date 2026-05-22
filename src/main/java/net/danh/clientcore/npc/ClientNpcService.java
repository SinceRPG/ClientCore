package net.danh.clientcore.npc;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class ClientNpcService implements Listener {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final FoliaScheduler scheduler;
    private final HookRegistry hooks;
    private final ConditionEvaluator conditions;
    private List<NpcRule> rules = List.of();
    private boolean enabled;
    private int refreshRadius;
    private ScheduledTask refreshTask;

    public ClientNpcService(Plugin plugin, ConfigManager configManager, FoliaScheduler scheduler, HookRegistry hooks, ClientPacketService packets, ConditionEvaluator conditions) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
        this.hooks = hooks;
        this.conditions = conditions;
    }

    public void reload() {
        this.enabled = configManager.getNpcs().getBoolean("client-npcs.enabled", true);
        this.refreshRadius = Math.max(1, configManager.getNpcs().getInt("client-npcs.refresh-radius", 32));
        this.rules = new NpcRuleLoader(plugin, configManager).load();

        if (refreshTask != null) refreshTask.cancel();

        long period = configManager.getNpcs().getLong("client-npcs.refresh-period-ticks", 40L);
        refreshTask = scheduler.globalTimer(20L, period, task -> {
            if (!enabled) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().execute(plugin, () -> refreshFor(player), null, 1L);
            }
        });
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
    }

    private void refreshFor(Player player) {
        if (!player.isOnline()) return;

        for (NpcRule rule : rules) {
            Location loc = rule.location();
            if (loc.getWorld() != player.getWorld()) continue;

            double dist = player.getLocation().distanceSquared(loc);
            boolean inRange = dist <= (refreshRadius * refreshRadius);
            boolean passed = conditions.evaluate(player, rule.condition(), rule.conditions()).passed();

            boolean shouldBeVisible = inRange && passed;

            if ("FANCYNPCS".equalsIgnoreCase(rule.providerType()) && hooks.hasFancyNpcs()) {
                hooks.toggleFancyNpcVisibility(player, rule.providerId(), shouldBeVisible);
            } else if ("CITIZENS".equalsIgnoreCase(rule.providerType()) && hooks.hasCitizens()) {
                hooks.toggleCitizensNpcVisibility(player, rule.providerId(), shouldBeVisible);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduler.regionLater(player.getLocation(), 20L, task -> refreshFor(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Nothing needed here since we don't manage activeNpcs
    }
}
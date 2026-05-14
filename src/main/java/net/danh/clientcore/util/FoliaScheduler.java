package net.danh.clientcore.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public final class FoliaScheduler {
    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public void region(Location location, Runnable runnable) {
        Bukkit.getRegionScheduler().execute(plugin, location, runnable);
    }

    public ScheduledTask regionLater(Location location, long delayTicks, Consumer<ScheduledTask> task) {
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, task, Math.max(1L, delayTicks));
    }

    public ScheduledTask regionTimer(Location location, long delayTicks, long periodTicks, Consumer<ScheduledTask> task) {
        return Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, task, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public ScheduledTask globalTimer(long delayTicks, long periodTicks, Consumer<ScheduledTask> task) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }
}

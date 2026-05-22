package net.danh.clientcore.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class FoliaScheduler {
    private final Plugin plugin;
    private final boolean folia;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.folia = classExists("io.papermc.paper.threadedregions.RegionizedServer");
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static void cancel(Object task) {
        if (task == null) return;
        try {
            Class<?> scheduledTask = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
            scheduledTask.getMethod("cancel").invoke(task);
            return;
        } catch (ClassNotFoundException ignored) {
            // Non-Folia servers cancel through BukkitTask method references before this helper is used.
        } catch (ReflectiveOperationException ignored) {
            // Fall through to the implementation-class fallback below.
        }

        try {
            Method cancel = task.getClass().getMethod("cancel");
            cancel.setAccessible(true);
            cancel.invoke(task);
        } catch (ReflectiveOperationException ignored) {
            // Cancellation is best-effort during shutdown; plugin disable must not fail because a task is already gone.
        }
    }

    public boolean isFolia() {
        return folia;
    }

    public void entity(Entity entity, Runnable runnable) {
        entityLater(entity, 1L, ignored -> runnable.run());
    }

    public CompatTask entityLater(Entity entity, long delayTicks, Consumer<CompatTask> task) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, () -> task.accept(() -> {
            }), Math.max(1L, delayTicks));
            return bukkitTask::cancel;
        }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            CompatTask[] holder = new CompatTask[1];
            Method execute = scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
            execute.invoke(scheduler, plugin, (Runnable) () -> task.accept(holder[0]), null, Math.max(1L, delayTicks));
            holder[0] = () -> {
            };
            return holder[0];
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to schedule entity task", ex);
        }
    }

    public void region(Location location, Runnable runnable) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return;
        }
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Method execute = regionScheduler.getClass().getMethod("execute", Plugin.class, Location.class, Runnable.class);
            execute.invoke(regionScheduler, plugin, location, runnable);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to schedule region task", ex);
        }
    }

    public CompatTask regionLater(Location location, long delayTicks, Consumer<CompatTask> task) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, () -> task.accept(() -> {
            }), Math.max(1L, delayTicks));
            return bukkitTask::cancel;
        }
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Object foliaTask = regionScheduler.getClass()
                    .getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class)
                    .invoke(regionScheduler, plugin, location, (Consumer<Object>) rawTask -> task.accept(() -> cancel(rawTask)), Math.max(1L, delayTicks));
            return () -> cancel(foliaTask);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to schedule delayed region task", ex);
        }
    }

    public CompatTask regionTimer(Location location, long delayTicks, long periodTicks, Consumer<CompatTask> task) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> task.accept(() -> {
            }), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
            return bukkitTask::cancel;
        }
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Object foliaTask = regionScheduler.getClass()
                    .getMethod("runAtFixedRate", Plugin.class, Location.class, Consumer.class, long.class, long.class)
                    .invoke(regionScheduler, plugin, location, (Consumer<Object>) rawTask -> task.accept(() -> cancel(rawTask)), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
            return () -> cancel(foliaTask);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to schedule region timer", ex);
        }
    }

    public CompatTask globalTimer(long delayTicks, long periodTicks, Consumer<CompatTask> task) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> task.accept(() -> {
            }), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
            return bukkitTask::cancel;
        }
        try {
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Object foliaTask = globalScheduler.getClass()
                    .getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class)
                    .invoke(globalScheduler, plugin, (Consumer<Object>) rawTask -> task.accept(() -> cancel(rawTask)), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
            return () -> cancel(foliaTask);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to schedule global timer", ex);
        }
    }
}

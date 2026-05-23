package net.danh.clientcore.block;

import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.util.CompatTask;
import net.danh.clientcore.util.FoliaScheduler;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientBlockSupportService implements Listener {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final FoliaScheduler scheduler;
    private final Map<UUID, Set<BlockKey>> solidBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> originalAllowFlight = new ConcurrentHashMap<>();
    private CompatTask monitorTask;
    private boolean enabled;
    private long monitorPeriodTicks;
    private double playerHalfWidth;
    private double footYOffset;

    public ClientBlockSupportService(Plugin plugin, ConfigManager configManager, FoliaScheduler scheduler) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
    }

    public void start() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        reload();
        monitorTask = scheduler.globalTimer(1L, monitorPeriodTicks, task -> {
            if (!enabled) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.entity(player, () -> update(player));
            }
        });
    }

    public void reload() {
        this.enabled = configManager.getMain().getBoolean("settings.client-block-support.enabled", true);
        this.monitorPeriodTicks = Math.max(1L, configManager.getMain().getLong("settings.client-block-support.monitor-period-ticks", 5L));
        this.playerHalfWidth = Math.max(0.01D, configManager.getMain().getDouble("settings.client-block-support.player-half-width", 0.3001D));
        this.footYOffset = Math.max(0.0D, configManager.getMain().getDouble("settings.client-block-support.foot-y-offset", 0.03D));
        if (!enabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                clear(player);
            }
        }
    }

    public void shutdown() {
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
        }
        if (plugin.isEnabled()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.entity(player, () -> restore(player));
            }
        }
        solidBlocks.clear();
        originalAllowFlight.clear();
    }

    public void sendBlock(Player player, Location location, BlockData blockData) {
        if (isSupportCandidate(blockData)) {
            solidBlocks.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(BlockKey.from(location));
        } else {
            removeBlock(player, location);
        }
        scheduler.entity(player, () -> update(player));
    }

    public void removeBlock(Player player, Location location) {
        Set<BlockKey> blocks = solidBlocks.get(player.getUniqueId());
        if (blocks == null) return;
        blocks.remove(BlockKey.from(location));
        if (blocks.isEmpty()) {
            solidBlocks.remove(player.getUniqueId());
        }
        scheduler.entity(player, () -> update(player));
    }

    public void clear(Player player) {
        solidBlocks.remove(player.getUniqueId());
        scheduler.entity(player, () -> restore(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        solidBlocks.remove(event.getPlayer().getUniqueId());
        originalAllowFlight.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        Boolean original = originalAllowFlight.get(player.getUniqueId());
        if (original == null || original) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        scheduler.entity(player, () -> {
            if (isManagedGameMode(event.getNewGameMode())) {
                update(player);
            } else {
                originalAllowFlight.remove(player.getUniqueId());
            }
        });
    }

    private void update(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (!isManagedGameMode(player.getGameMode())) {
            originalAllowFlight.remove(player.getUniqueId());
            return;
        }
        if (isStandingOnClientBlock(player)) {
            protect(player);
        } else {
            restore(player);
        }
    }

    private void protect(Player player) {
        originalAllowFlight.computeIfAbsent(player.getUniqueId(), ignored -> player.getAllowFlight());
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
        if (player.isFlying()) {
            player.setFlying(false);
        }
    }

    private void restore(Player player) {
        Boolean original = originalAllowFlight.remove(player.getUniqueId());
        if (original == null || !player.isOnline() || !isManagedGameMode(player.getGameMode())) {
            return;
        }
        if (!original && player.isFlying()) {
            player.setFlying(false);
        }
        player.setAllowFlight(original);
    }

    private boolean isStandingOnClientBlock(Player player) {
        Set<BlockKey> blocks = solidBlocks.get(player.getUniqueId());
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }

        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        int y = (int) Math.floor(location.getY() - footYOffset);
        return contains(blocks, world, location.getX(), y, location.getZ())
                || contains(blocks, world, location.getX() - playerHalfWidth, y, location.getZ() - playerHalfWidth)
                || contains(blocks, world, location.getX() - playerHalfWidth, y, location.getZ() + playerHalfWidth)
                || contains(blocks, world, location.getX() + playerHalfWidth, y, location.getZ() - playerHalfWidth)
                || contains(blocks, world, location.getX() + playerHalfWidth, y, location.getZ() + playerHalfWidth);
    }

    private boolean contains(Set<BlockKey> blocks, World world, double x, int y, double z) {
        return blocks.contains(new BlockKey(world.getName(), (int) Math.floor(x), y, (int) Math.floor(z)));
    }

    private boolean isManagedGameMode(GameMode gameMode) {
        return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
    }

    private boolean isSupportCandidate(BlockData blockData) {
        Material material = blockData.getMaterial();
        return !material.isAir() && material.isSolid();
    }

    private record BlockKey(String world, int x, int y, int z) {
        private static BlockKey from(Location location) {
            World world = location.getWorld();
            return new BlockKey(world == null ? "" : world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}

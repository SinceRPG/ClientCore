package net.danh.clientcore.luck;

import net.danh.clientcore.storage.PlayerStats;
import net.danh.clientcore.storage.StorageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LuckService implements Listener {
    private final StorageService storage;
    private final ConcurrentMap<UUID, PlayerStats> cache = new ConcurrentHashMap<>();

    public LuckService(StorageService storage) {
        this.storage = storage;
    }

    public void load(Player player) {
        storage.loadPlayer(player.getUniqueId(), player.getName())
                .thenAccept(stats -> cache.put(player.getUniqueId(), stats))
                .exceptionally(error -> null);
    }

    public double luck(Player player) {
        return cache.getOrDefault(player.getUniqueId(), new PlayerStats(player.getUniqueId(), player.getName(), 0.0D, false)).luck();
    }

    public PlayerStats snapshot(Player player) {
        return cache.getOrDefault(player.getUniqueId(), new PlayerStats(player.getUniqueId(), player.getName(), 0.0D, false));
    }

    public CompletableFuture<PlayerStats> load(UUID uuid, String name) {
        return storage.loadPlayer(uuid, name).thenApply(stats -> {
            cache.put(uuid, stats);
            return stats;
        });
    }

    public PlayerStats set(UUID uuid, String name, double luck) {
        PlayerStats current = cache.getOrDefault(uuid, new PlayerStats(uuid, name == null ? uuid.toString() : name, 0.0D, false));
        PlayerStats next = current.withName(name).withLuck(luck);
        cache.put(uuid, next);
        storage.savePlayer(next);
        return next;
    }

    public PlayerStats add(UUID uuid, String name, double amount) {
        PlayerStats current = cache.getOrDefault(uuid, new PlayerStats(uuid, name == null ? uuid.toString() : name, 0.0D, false));
        return set(uuid, name, current.luck() + amount);
    }

    public PlayerStats excludeTop(UUID uuid, String name, boolean excluded) {
        PlayerStats current = cache.getOrDefault(uuid, new PlayerStats(uuid, name == null ? uuid.toString() : name, 0.0D, false));
        PlayerStats next = current.withName(name).withExcludedFromTop(excluded);
        cache.put(uuid, next);
        storage.savePlayer(next);
        return next;
    }

    public CompletableFuture<List<PlayerStats>> top(int limit) {
        return storage.topLuck(limit);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}

package net.danh.clientcore.storage;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches and manages player cooldowns in memory, writing changes back to the SQL storage async.
 */
public final class CooldownManager implements Listener {
    private final StorageService storage;
    private final Map<UUID, Map<String, Long>> cache = new ConcurrentHashMap<>();

    public CooldownManager(StorageService storage) {
        this.storage = storage;
    }

    public void setCooldown(UUID uuid, String category, String ruleId, long expiresAtMillis) {
        Map<String, Long> playerCooldowns = cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        String key = category + ":" + ruleId;
        playerCooldowns.put(key, expiresAtMillis);
        storage.saveCooldown(uuid, category, ruleId, expiresAtMillis);
    }

    public boolean isOnCooldown(UUID uuid, String category, String ruleId) {
        Map<String, Long> playerCooldowns = cache.get(uuid);
        if (playerCooldowns == null) return false;
        Long expiresAt = playerCooldowns.get(category + ":" + ruleId);
        return expiresAt != null && System.currentTimeMillis() < expiresAt;
    }

    public long getRemainingMillis(UUID uuid, String category, String ruleId) {
        Map<String, Long> playerCooldowns = cache.get(uuid);
        if (playerCooldowns == null) return 0L;
        Long expiresAt = playerCooldowns.get(category + ":" + ruleId);
        if (expiresAt == null) return 0L;
        long diff = expiresAt - System.currentTimeMillis();
        return Math.max(0L, diff);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        storage.loadCooldowns(event.getPlayer().getUniqueId()).thenAccept(cooldowns -> {
            cache.put(event.getPlayer().getUniqueId(), new ConcurrentHashMap<>(cooldowns));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
package net.danh.clientcore.visibility;

import net.danh.clientcore.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VisibilityService implements Listener {
    private final Plugin plugin;
    private final FoliaScheduler scheduler;
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    public VisibilityService(Plugin plugin, FoliaScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public boolean toggle(Player player) {
        if (hidden.contains(player.getUniqueId())) {
            show(player);
            return false;
        }
        hide(player);
        return true;
    }

    public void hide(Player player) {
        hidden.add(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId())) {
                scheduler.entity(other, () -> {
                    if (other.isOnline()) other.hidePlayer(plugin, player);
                });
            }
        }
    }

    public void show(Player player) {
        hidden.remove(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId())) {
                scheduler.entity(other, () -> {
                    if (other.isOnline()) other.showPlayer(plugin, player);
                });
            }
        }
    }

    public boolean isHidden(Player player) {
        return hidden.contains(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (hidden.contains(online.getUniqueId()) && !online.getUniqueId().equals(joined.getUniqueId())) {
                scheduler.entity(joined, () -> {
                    if (joined.isOnline()) joined.hidePlayer(plugin, online);
                });
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hidden.remove(event.getPlayer().getUniqueId());
    }
}

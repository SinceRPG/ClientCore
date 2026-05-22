package net.danh.clientcore.hook.plugin;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class FancyNpcsHook {
    public static void toggleVisibility(Plugin plugin, String npcName, Player player, boolean visible) {
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) return;

        Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpc(npcName);
        if (npc == null) return;

        Runnable task = () -> {
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(FancyNpcsPlugin.class.getClassLoader());
                if (visible) {
                    if (!npc.getIsVisibleForPlayer().containsKey(player.getUniqueId()) || !npc.getIsVisibleForPlayer().get(player.getUniqueId())) {
                        npc.getIsVisibleForPlayer().put(player.getUniqueId(), true);
                        npc.spawn(player);
                    }
                } else {
                    if (!npc.getIsVisibleForPlayer().containsKey(player.getUniqueId()) || npc.getIsVisibleForPlayer().get(player.getUniqueId())) {
                        npc.getIsVisibleForPlayer().put(player.getUniqueId(), false);
                        npc.remove(player);
                    }
                }
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        };

        if (plugin.isEnabled()) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            task.run();
        }
    }
}
package net.danh.clientcore.hook.plugin;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CitizensHook {
    public static void toggleVisibility(Plugin plugin, String npcId, Player player, boolean visible) {
        try {
            int id = Integer.parseInt(npcId);
            NPC npc = CitizensAPI.getNPCRegistry().getById(id);
            if (npc != null && npc.isSpawned()) {
                Entity entity = npc.getEntity();
                if (entity != null) {
                    if (visible) {
                        player.showEntity(plugin, entity);
                    } else {
                        player.hideEntity(plugin, entity);
                    }
                }
            }
        } catch (NumberFormatException ignored) {
            // Invalid ID format
        }
    }
}
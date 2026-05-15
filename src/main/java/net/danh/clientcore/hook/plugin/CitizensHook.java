package net.danh.clientcore.hook.plugin;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

public final class CitizensHook {
    public static Entity spawn(Plugin plugin, ConfigManager configManager, String name, Location loc, EntityType type) {
        try {
            NPCRegistry registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
            NPC npc = registry.createNPC(type, name);
            if (npc.spawn(loc)) {
                return npc.getEntity();
            }
            return null;
        } catch (Throwable t) {
            Text.warn(plugin, configManager, "console.hook-failed", "{plugin}", "Citizens", "{error}", t.getMessage());
            return null;
        }
    }
}
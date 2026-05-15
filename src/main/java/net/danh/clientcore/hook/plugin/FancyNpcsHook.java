package net.danh.clientcore.hook.plugin;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.npc.ClientNpc;
import net.danh.clientcore.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class FancyNpcsHook {
    public static ClientNpc spawn(Plugin plugin, ConfigManager configManager, String name, Location loc, EntityType type, Player viewer) {
        try {
            UUID npcId = UUID.randomUUID();
            NpcData data = new NpcData(npcId.toString(), viewer.getUniqueId(), loc);
            data.setType(type);
            data.setDisplayName(name);
            Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
            npc.create();
            npc.spawn(viewer);
            return new ClientNpc() {
                @Override
                public void remove(Player p) {
                    npc.remove(p);
                    FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
                }

                @Override
                public Object getHandle() {
                    return npc;
                }
            };
        } catch (Throwable t) {
            Text.warn(plugin, configManager, "console.hook-failed", "{plugin}", "FancyNpcs", "{error}", t.getMessage());
            return null;
        }
    }
}
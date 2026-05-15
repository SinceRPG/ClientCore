package net.danh.clientcore.npc;

import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class NpcRuleLoader {
    private final Plugin plugin;
    private final ConfigManager configManager;

    public NpcRuleLoader(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public List<NpcRule> load() {
        List<NpcRule> rules = new ArrayList<>();
        ConfigurationSection root = configManager.getNpcs().getConfigurationSection("client-npcs.rules");
        if (root == null) return rules;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;

            EntityType type = EntityType.VILLAGER;
            try {
                type = EntityType.valueOf(section.getString("entity-type", "VILLAGER").toUpperCase());
            } catch (IllegalArgumentException e) {
                Text.warn(plugin, configManager, "console.invalid-entity", "{rule}", id, "{type}", section.getString("entity-type"));
            }

            String worldName = section.getString("location.world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                Text.warn(plugin, configManager, "console.world-not-found", "{rule}", id, "{world}", worldName);
                continue;
            }

            Location location = new Location(
                    world,
                    section.getDouble("location.x"),
                    section.getDouble("location.y"),
                    section.getDouble("location.z"),
                    (float) section.getDouble("location.yaw", 0.0),
                    (float) section.getDouble("location.pitch", 0.0)
            );

            rules.add(new NpcRule(
                    id,
                    true,
                    section.getString("provider-type", "VANILLA"),
                    section.getString("provider-id", ""),
                    type,
                    section.getString("name", ""),
                    location,
                    section.getString("condition", ""),
                    section.getStringList("conditions")
            ));
        }
        return rules;
    }
}
package net.danh.clientcore.drop;

import net.danh.clientcore.condition.CooldownRule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DropRuleLoader {
    private final Plugin plugin;
    private final YamlConfiguration config;

    public DropRuleLoader(Plugin plugin, YamlConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public List<DropRule> load() {
        List<DropRule> rules = new ArrayList<>();
        ConfigurationSection root = config.getConfigurationSection("client-drops.rules");
        if (root == null) return rules;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;

            String worldName = section.getString("location.world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Location location = new Location(
                    world,
                    section.getDouble("location.x"),
                    section.getDouble("location.y"),
                    section.getDouble("location.z")
            );

            List<CooldownRule> cooldowns = new ArrayList<>();
            if (section.contains("cooldowns")) {
                for (Map<?, ?> map : section.getMapList("cooldowns")) {
                    ConfigurationSection cd = section.createSection("__temp", map);
                    cooldowns.add(new CooldownRule(cd.getString("condition", ""), cd.getStringList("conditions"), cd.getLong("duration-ticks", 0)));
                }
            }

            rules.add(new DropRule(
                    id,
                    section.getConfigurationSection("item"),
                    location,
                    section.getString("condition", ""),
                    section.getStringList("conditions"),
                    cooldowns
            ));
        }
        return rules;
    }
}
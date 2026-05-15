package net.danh.clientcore.chest;

import net.danh.clientcore.condition.CooldownRule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LootChestRuleLoader {
    private final Plugin plugin;
    private final YamlConfiguration config;

    public LootChestRuleLoader(Plugin plugin, YamlConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public List<LootChestRule> load() {
        List<LootChestRule> rules = new ArrayList<>();
        ConfigurationSection root = config.getConfigurationSection("client-loot-chests.rules");
        if (root == null) return rules;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;

            String worldName = section.getString("location.world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Location location = new Location(
                    world,
                    section.getInt("location.x"),
                    section.getInt("location.y"),
                    section.getInt("location.z")
            );

            Material display = Material.matchMaterial(section.getString("display-block", "CHEST"));
            if (display == null) display = Material.CHEST;

            List<ConfigurationSection> drops = new ArrayList<>();
            for (var value : section.getMapList("drops")) {
                drops.add(section.createSection("__drop_" + drops.size(), value));
            }

            List<CooldownRule> cooldowns = new ArrayList<>();
            if (section.contains("cooldowns")) {
                for (Map<?, ?> map : section.getMapList("cooldowns")) {
                    ConfigurationSection cd = section.createSection("__temp", map);
                    cooldowns.add(new CooldownRule(cd.getString("condition", ""), cd.getStringList("conditions"), cd.getLong("duration-ticks", 0)));
                }
            }

            rules.add(new LootChestRule(
                    id,
                    location,
                    Bukkit.createBlockData(display),
                    section.getString("gui-title", "<gold>Loot Chest"),
                    section.getString("condition", ""),
                    section.getStringList("conditions"),
                    cooldowns,
                    drops
            ));
        }
        return rules;
    }
}
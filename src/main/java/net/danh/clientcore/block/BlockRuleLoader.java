package net.danh.clientcore.block;

import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

final class BlockRuleLoader {
    private final Plugin plugin;
    private final YamlConfiguration config;

    BlockRuleLoader(Plugin plugin, YamlConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    List<BlockRule> load() {
        List<BlockRule> rules = new ArrayList<>();
        Set<String> blockNames = Registry.BLOCK.keyStream()
                .map(key -> key.getKey().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        ConfigurationSection root = config.getConfigurationSection("block-regen.rules");
        if (root == null) {
            return rules;
        }
        String defaultFlag = config.getString("block-regen.default-worldguard-flag", "clientcore-regen");
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            String worldName = section.getString("location.world", "world");
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) continue;
            org.bukkit.Location location = new org.bukkit.Location(
                    world,
                    section.getDouble("location.x"),
                    section.getDouble("location.y"),
                    section.getDouble("location.z")
            );

            String readyBlock = section.getString("ready-block", "AIR").toUpperCase(Locale.ROOT);
            String cooldownBlock = section.getString("cooldown-block", "AIR").toUpperCase(Locale.ROOT);

            List<ConfigurationSection> drops = new ArrayList<>();
            for (var value : section.getMapList("drops")) {
                ConfigurationSection drop = section.createSection("__drop_" + drops.size(), value);
                drops.add(drop);
            }
            List<BlockVariant> variants = new ArrayList<>();
            for (var value : section.getMapList("variants")) {
                ConfigurationSection variant = section.createSection("__variant_" + variants.size(), value);

                String variantReady = variant.getString("ready-block", readyBlock).toUpperCase(Locale.ROOT);
                String variantCooldown = variant.getString("cooldown-block", cooldownBlock).toUpperCase(Locale.ROOT);

                List<ConfigurationSection> variantDrops = new ArrayList<>();
                for (var dropValue : variant.getMapList("drops")) {
                    ConfigurationSection drop = variant.createSection("__drop_" + variantDrops.size(), dropValue);
                    variantDrops.add(drop);
                }
                if (variantDrops.isEmpty()) {
                    variantDrops = drops;
                }
                variants.add(new BlockVariant(
                        variant.getString("id", "variant_" + variants.size()),
                        variantReady,
                        variantCooldown,
                        Math.max(0.0D, variant.getDouble("weight", 1.0D)),
                        variant.getBoolean("rare", false),
                        Math.max(0.0D, variant.getDouble("luck-multiplier", 1.0D)),
                        new HashSet<>(variant.getStringList("required-condition-ids")),
                        Math.max(1, variant.getInt("regen-ticks", section.getInt("regen-ticks", 100))),
                        variantDrops
                ));
            }
            if (variants.isEmpty()) {
                variants.add(new BlockVariant(
                        "default",
                        readyBlock,
                        cooldownBlock,
                        Math.max(0.0D, section.getDouble("weight", 1.0D)),
                        section.getBoolean("rare", false),
                        Math.max(0.0D, section.getDouble("luck-multiplier", 1.0D)),
                        new HashSet<>(section.getStringList("required-condition-ids")),
                        Math.max(1, section.getInt("regen-ticks", 100)),
                        drops
                ));
            }
            rules.add(new BlockRule(
                    id,
                    location,
                    section.getString("condition", ""),
                    section.getStringList("conditions"),
                    section.getString("worldguard-flag", defaultFlag),
                    variants
            ));
        }
        return rules;
    }
}
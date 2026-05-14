package net.danh.clientcore.block;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class BlockRuleLoader {
    private final Plugin plugin;

    BlockRuleLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    List<BlockRule> load() {
        List<BlockRule> rules = new ArrayList<>();
        Set<String> blockNames = Registry.BLOCK.keyStream()
                .map(key -> key.getKey().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("block-regen.rules");
        if (root == null) {
            return rules;
        }
        String defaultFlag = plugin.getConfig().getString("block-regen.default-worldguard-flag", "clientcore-regen");
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            Set<Material> sourceBlocks = new HashSet<>();
            for (String raw : section.getStringList("source-blocks")) {
                Material material = Material.matchMaterial(raw);
                if (material != null && blockNames.contains(material.name())) {
                    sourceBlocks.add(material);
                }
            }
            Material display = Material.matchMaterial(section.getString("display-block", "STONE"));
            if (display == null || !blockNames.contains(display.name())) {
                display = Material.STONE;
            }
            List<ConfigurationSection> drops = new ArrayList<>();
            for (var value : section.getMapList("drops")) {
                ConfigurationSection drop = section.createSection("__drop_" + drops.size(), value);
                drops.add(drop);
            }
            List<BlockVariant> variants = new ArrayList<>();
            for (var value : section.getMapList("variants")) {
                ConfigurationSection variant = section.createSection("__variant_" + variants.size(), value);
                Material variantDisplay = Material.matchMaterial(variant.getString("display-block", display.name()));
                if (variantDisplay == null || !blockNames.contains(variantDisplay.name())) {
                    variantDisplay = display;
                }
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
                        Bukkit.createBlockData(variantDisplay),
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
                        Bukkit.createBlockData(display),
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
                    section.getInt("priority", 0),
                    new HashSet<>(section.getStringList("worlds").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList()),
                    sourceBlocks,
                    section.getString("condition", ""),
                    section.getStringList("conditions"),
                    section.getString("worldguard-flag", defaultFlag),
                    section.getBoolean("grow-animation.enabled", true),
                    Math.max(1, section.getInt("grow-animation.frames", 12)),
                    variants
            ));
        }
        rules.sort(Comparator.comparingInt(BlockRule::priority).reversed());
        return rules;
    }
}

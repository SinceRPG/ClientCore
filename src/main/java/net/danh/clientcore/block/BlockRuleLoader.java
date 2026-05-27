package net.danh.clientcore.block;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.*;

final class BlockRuleLoader {
    private final Plugin plugin;
    private final YamlConfiguration config;

    BlockRuleLoader(Plugin plugin, YamlConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    List<BlockRule> load() {
        List<BlockRule> rules = new ArrayList<>();
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
                        loadMining(variant, section),
                        loadFarming(variant, section),
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
                        loadMining(section, null),
                        loadFarming(section, null),
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

    Map<Material, VanillaBlockMiningRule> loadVanillaMiningRules() {
        Map<Material, VanillaBlockMiningRule> rules = new EnumMap<>(Material.class);
        ConfigurationSection root = config.getConfigurationSection("vanilla-mining.blocks");
        if (root == null || !config.getBoolean("vanilla-mining.enabled", false)) {
            return rules;
        }
        String defaultFlag = config.getString("vanilla-mining.default-worldguard-flag", "");
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            Material material = Material.matchMaterial(section.getString("material", key));
            if (material == null || !material.isBlock()) {
                continue;
            }

            List<ConfigurationSection> drops = new ArrayList<>();
            for (var value : section.getMapList("drops")) {
                ConfigurationSection drop = section.createSection("__drop_" + drops.size(), value);
                drops.add(drop);
            }

            rules.put(material, new VanillaBlockMiningRule(
                    material,
                    section.getString("condition", ""),
                    section.getStringList("conditions"),
                    section.getString("worldguard-flag", defaultFlag),
                    loadMining(section, null),
                    drops
            ));
        }
        return rules;
    }

    private BlockMiningConfig loadMining(ConfigurationSection section, ConfigurationSection fallback) {
        ConfigurationSection mining = section.getConfigurationSection("mining");
        if (mining == null && fallback != null) {
            mining = fallback.getConfigurationSection("mining");
        }
        if (mining == null) {
            return new BlockMiningConfig("", "BLOCK_DISPLAY", 0, defaultFeedback(null), List.of());
        }

        String activeBlock = mining.getString("active-block", "BARRIER");
        String visualMode = normalizeVisualMode(mining.getString("visual-mode",
                mining.getBoolean("active-block-visual", false) ? "ACTIVE_BLOCK" : "BLOCK_DISPLAY"));
        int defaultTime = Math.max(1, mining.getInt("default-time-ticks", mining.getInt("time-ticks", 0)));
        BlockMiningFeedback feedback = defaultFeedback(mining.getConfigurationSection("feedback"));
        List<BlockToolRule> tools = loadToolRules(mining, defaultTime);
        return new BlockMiningConfig(activeBlock, visualMode, defaultTime, feedback, tools);
    }

    private BlockFarmingConfig loadFarming(ConfigurationSection section, ConfigurationSection fallback) {
        ConfigurationSection farming = section.getConfigurationSection("farming");
        if (farming == null && fallback != null) {
            farming = fallback.getConfigurationSection("farming");
        }
        if (farming == null || !farming.getBoolean("enabled", true)) {
            return new BlockFarmingConfig(false, List.of(), List.of());
        }
        return new BlockFarmingConfig(true, loadToolRules(farming, 1), loadFarmingStages(farming));
    }

    private List<BlockFarmingStage> loadFarmingStages(ConfigurationSection farming) {
        List<BlockFarmingStage> stages = new ArrayList<>();
        for (var value : farming.getMapList("stages")) {
            ConfigurationSection stage = farming.createSection("__stage_" + stages.size(), value);
            List<ConfigurationSection> drops = new ArrayList<>();
            for (var dropValue : stage.getMapList("drops")) {
                ConfigurationSection drop = stage.createSection("__drop_" + drops.size(), dropValue);
                drops.add(drop);
            }
            String block = stage.getString("block", stage.getString("ready-block", ""));
            if (block == null || block.isBlank()) {
                String material = stage.getString("material", "");
                if (!material.isBlank() && stage.contains("age")) {
                    block = material + "[age=" + Math.max(0, stage.getInt("age")) + "]";
                } else {
                    block = material;
                }
            }
            stages.add(new BlockFarmingStage(
                    block == null ? "" : block,
                    Math.max(1, stage.getInt("after-ticks", stage.getInt("ticks", 1))),
                    drops
            ));
        }
        return stages;
    }

    private List<BlockToolRule> loadToolRules(ConfigurationSection parent, int defaultTime) {
        List<BlockToolRule> tools = new ArrayList<>();
        for (var value : parent.getMapList("tools")) {
            ConfigurationSection tool = parent.createSection("__tool_" + tools.size(), value);
            ConfigurationSection item = tool.getConfigurationSection("item");
            ConfigurationSection source = item != null ? item : tool;
            String type = source.getString("type", "vanilla").toLowerCase(Locale.ROOT);
            Material material = Material.matchMaterial(source.getString("material", ""));
            String mmoType = source.getString("mmo-type", source.getString("mmo_type"));
            String mmoId = source.getString("mmo-id", source.getString("mmo_id"));

            List<ConfigurationSection> toolDrops = new ArrayList<>();
            for (var dropValue : tool.getMapList("drops")) {
                ConfigurationSection drop = tool.createSection("__drop_" + toolDrops.size(), dropValue);
                toolDrops.add(drop);
            }

            tools.add(new BlockToolRule(
                    type,
                    material,
                    mmoType,
                    mmoId,
                    Math.max(1, tool.getInt("time-ticks", defaultTime)),
                    toolDrops
            ));
        }
        return tools;
    }

    private String normalizeVisualMode(String input) {
        if (input == null || input.isBlank()) {
            return "BLOCK_DISPLAY";
        }
        String normalized = input.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVE_BLOCK", "RESOURCE_PACK", "RESOURCE_PACK_BLOCK", "VANILLA_CRACK" -> "ACTIVE_BLOCK";
            default -> "BLOCK_DISPLAY";
        };
    }

    private BlockMiningFeedback defaultFeedback(ConfigurationSection section) {
        if (section == null) {
            return new BlockMiningFeedback(true, false, true, true, 4,
                    "<gray>Mining <white>{progress}%</white>",
                    "<bold>{bar}</bold> <white>{progress}%</white>",
                    12,
                    "<gold>",
                    "<yellow>",
                    "<green>",
                    "<dark_gray>",
                    0x8C0C1016);
        }
        return new BlockMiningFeedback(
                section.getBoolean("display", true),
                section.getBoolean("actionbar", false),
                section.getBoolean("particles", true),
                section.getBoolean("sounds", true),
                Math.max(1, section.getInt("interval-ticks", 4)),
                section.getString("message", "<gray>Mining <white>{progress}%</white>"),
                section.getString("display-format", "<bold>{bar}</bold> <white>{progress}%</white>"),
                Math.max(1, section.getInt("bar-length", 12)),
                section.getString("low-color", "<gold>"),
                section.getString("mid-color", "<yellow>"),
                section.getString("high-color", "<green>"),
                section.getString("empty-color", "<dark_gray>"),
                parseColor(section.getString("background-argb", "8C0C1016"), 0x8C0C1016)
        );
    }

    private int parseColor(String input, int fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        try {
            return (int) Long.parseLong(input.replace("#", ""), 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

package net.danh.clientcore.block;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

record MiningEnchantEffects(
        boolean efficiencyEnabled,
        String efficiencyFormula,
        int minTimeTicks,
        boolean fortuneEnabled,
        String fortuneBonusFormula,
        List<CustomEnchantEffect> custom
) {
    static MiningEnchantEffects load(ConfigurationSection root) {
        ConfigurationSection section = enchantEffectsSection(root);
        if (section == null) {
            return defaults();
        }
        ConfigurationSection efficiency = section.getConfigurationSection("efficiency");
        ConfigurationSection fortune = section.getConfigurationSection("fortune");
        return new MiningEnchantEffects(
                efficiency == null || efficiency.getBoolean("enabled", true),
                efficiency == null ? "level * random(1, level)" : efficiency.getString("reduction-percent-formula", "level * random(1, level)"),
                Math.max(1, efficiency == null ? 1 : efficiency.getInt("min-time-ticks", 1)),
                fortune == null || fortune.getBoolean("enabled", true),
                fortune == null ? "random(0, level)" : fortune.getString("bonus-amount-formula", "random(0, level)"),
                loadCustom(section)
        );
    }

    private static ConfigurationSection enchantEffectsSection(ConfigurationSection root) {
        if (root == null) {
            return null;
        }
        ConfigurationSection section = root.getConfigurationSection("block-regen.enchant-effects");
        if (section != null) {
            return section;
        }
        section = root.getConfigurationSection("vanilla-mining.enchant-effects");
        if (section != null) {
            return section;
        }
        return root.getConfigurationSection("enchant-effects");
    }

    private static List<CustomEnchantEffect> loadCustom(ConfigurationSection section) {
        List<CustomEnchantEffect> effects = new ArrayList<>();
        ConfigurationSection custom = section.getConfigurationSection("custom");
        if (custom != null) {
            for (String key : custom.getKeys(false)) {
                if (custom.isConfigurationSection(key)) {
                    addCustomEffect(effects, custom.getConfigurationSection(key), key);
                }
            }
        }
        for (Map<?, ?> value : section.getMapList("custom")) {
            addCustomEffect(effects, detachedSection(value), "");
        }
        return List.copyOf(effects);
    }

    private static void addCustomEffect(List<CustomEnchantEffect> effects, ConfigurationSection section, String fallbackId) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        String id = section.getString("id", section.getString("enchant", fallbackId));
        if (id == null || id.isBlank()) {
            return;
        }
        String mode = section.getString("mode", section.getString("effect", "time-reduction"));
        String formula = section.getString("formula", section.getString("reduction-percent-formula",
                section.getString("bonus-amount-formula", "0")));
        effects.add(new CustomEnchantEffect(
                section.getString("type", "sinceenchantments"),
                id,
                normalizeMode(mode),
                formula,
                Math.max(1, section.getInt("min-time-ticks", 1))
        ));
    }

    private static String normalizeMode(String input) {
        if (input == null || input.isBlank()) {
            return "TIME_REDUCTION";
        }
        String normalized = input.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DROP_BONUS", "FORTUNE", "BONUS_AMOUNT" -> "DROP_BONUS";
            default -> "TIME_REDUCTION";
        };
    }

    private static ConfigurationSection detachedSection(Map<?, ?> values) {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("value");
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            section.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return section;
    }

    private static MiningEnchantEffects defaults() {
        return new MiningEnchantEffects(true, "level * random(1, level)", 1, true, "random(0, level)", List.of());
    }

    record CustomEnchantEffect(String type, String id, String mode, String formula, int minTimeTicks) {
    }
}

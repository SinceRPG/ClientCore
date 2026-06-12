package net.danh.clientcore.block;

import org.bukkit.configuration.ConfigurationSection;

record MiningEnchantEffects(
        boolean efficiencyEnabled,
        String efficiencyFormula,
        int minTimeTicks,
        boolean fortuneEnabled,
        String fortuneBonusFormula
) {
    static MiningEnchantEffects load(ConfigurationSection root) {
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("vanilla-mining.enchant-effects");
        if (section == null && root != null) {
            section = root.getConfigurationSection("enchant-effects");
        }
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
                fortune == null ? "random(0, level)" : fortune.getString("bonus-amount-formula", "random(0, level)")
        );
    }

    private static MiningEnchantEffects defaults() {
        return new MiningEnchantEffects(true, "level * random(1, level)", 1, true, "random(0, level)");
    }
}

package net.danh.clientcore.block;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

record BlockToolRule(
        String type,
        Material material,
        String mmoType,
        String mmoId,
        List<BlockEnchantRule> enchants,
        int timeTicks,
        int regenTicks,
        List<ProfessionExpReward> professionExp,
        List<ConfigurationSection> drops
) {
}

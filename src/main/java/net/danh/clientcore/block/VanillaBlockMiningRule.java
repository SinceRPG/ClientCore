package net.danh.clientcore.block;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

record VanillaBlockMiningRule(
        Material material,
        String condition,
        List<String> conditions,
        String worldGuardFlag,
        BlockMiningConfig mining,
        List<ProfessionExpReward> professionExp,
        List<ConfigurationSection> drops
) {
}

package net.danh.clientcore.block;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

record BlockFarmingStage(
        String block,
        int afterTicks,
        List<ProfessionExpReward> professionExp,
        List<ConfigurationSection> drops
) {
}

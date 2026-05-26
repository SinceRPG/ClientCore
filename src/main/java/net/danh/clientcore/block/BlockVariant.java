package net.danh.clientcore.block;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Set;

record BlockVariant(
        String id,
        String readyBlock,
        String cooldownBlock,
        double weight,
        boolean rare,
        double luckMultiplier,
        Set<String> requiredConditionIds,
        int regenTicks,
        BlockMiningConfig mining,
        BlockFarmingConfig farming,
        List<ConfigurationSection> drops
) {
}

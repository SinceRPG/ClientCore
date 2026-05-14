package net.danh.clientcore.block;

import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Set;

record BlockVariant(
        String id,
        BlockData displayBlock,
        double weight,
        boolean rare,
        double luckMultiplier,
        Set<String> requiredConditionIds,
        int regenTicks,
        List<ConfigurationSection> drops
) {
}

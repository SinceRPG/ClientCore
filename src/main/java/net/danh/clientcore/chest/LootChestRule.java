package net.danh.clientcore.chest;

import net.danh.clientcore.condition.CooldownRule;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public record LootChestRule(
        String id,
        Location location,
        BlockData displayBlock,
        String guiTitle,
        String condition,
        List<String> conditions,
        List<CooldownRule> cooldowns,
        List<ConfigurationSection> drops
) {
}
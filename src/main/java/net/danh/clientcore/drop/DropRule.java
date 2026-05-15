package net.danh.clientcore.drop;

import net.danh.clientcore.condition.CooldownRule;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public record DropRule(
        String id,
        ConfigurationSection itemConfig,
        Location location,
        String condition,
        List<String> conditions,
        List<CooldownRule> cooldowns
) {
}
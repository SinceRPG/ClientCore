package net.danh.clientcore.mob;

import org.bukkit.entity.EntityType;

import java.util.Set;

record MobVariant(
        String id,
        String mythicMobId,
        EntityType fallbackEntity,
        double weight,
        boolean rare,
        double luckMultiplier,
        Set<String> requiredConditionIds,
        double health,
        double damage
) {
}

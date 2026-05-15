package net.danh.clientcore.npc;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.List;

public record NpcRule(
        String id,
        boolean enabled,
        String providerType,
        String providerId,
        EntityType entityType,
        String name,
        Location location,
        String condition,
        List<String> conditions
) {
}
package net.danh.clientcore.block;

import org.bukkit.Location;

import java.util.List;

record BlockRule(
        String id,
        Location location,
        String condition,
        List<String> conditions,
        String worldGuardFlag,
        List<BlockVariant> variants
) {
}
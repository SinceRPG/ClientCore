package net.danh.clientcore.block;

import org.bukkit.Material;

import java.util.List;
import java.util.Set;

record BlockRule(
        String id,
        int priority,
        Set<String> worlds,
        Set<Material> sourceBlocks,
        String condition,
        List<String> conditions,
        String worldGuardFlag,
        boolean animation,
        int frames,
        float viewRange,
        List<BlockVariant> variants
) {
}
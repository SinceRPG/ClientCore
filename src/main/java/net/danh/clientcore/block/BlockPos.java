package net.danh.clientcore.block;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

record BlockPos(String world, int x, int y, int z) {
    static BlockPos of(Block block) {
        return new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    Location center(World world) {
        return new Location(world, x + 0.5D, y + 0.5D, z + 0.5D);
    }

    Location blockLocation(World world) {
        return new Location(world, x, y, z);
    }
}

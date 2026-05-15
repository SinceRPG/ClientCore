package net.danh.clientcore.npc;

import org.bukkit.entity.Player;

/**
 * Unified interface abstracting how client NPCs are handled (Vanilla, Mythic, FancyNpcs, etc.)
 */
public interface ClientNpc {
    void remove(Player viewer);

    Object getHandle();
}
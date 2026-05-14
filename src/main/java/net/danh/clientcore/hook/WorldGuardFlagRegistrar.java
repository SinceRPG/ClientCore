package net.danh.clientcore.hook;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import org.bukkit.plugin.Plugin;

public final class WorldGuardFlagRegistrar {
    public static final StateFlag CLIENTCORE_REGEN = new StateFlag("clientcore-regen", true);

    private WorldGuardFlagRegistrar() {
    }

    public static void register(Plugin plugin) {
        try {
            WorldGuard.getInstance().getFlagRegistry().register(CLIENTCORE_REGEN);
        } catch (FlagConflictException ignored) {
            plugin.getLogger().info("WorldGuard flag clientcore-regen already exists, using the registered flag.");
        }
    }
}

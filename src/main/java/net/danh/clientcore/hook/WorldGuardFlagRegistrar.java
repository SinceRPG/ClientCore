package net.danh.clientcore.hook;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.util.Text;
import org.bukkit.plugin.Plugin;

public final class WorldGuardFlagRegistrar {
    public static final StateFlag CLIENTCORE_REGEN = new StateFlag("clientcore-regen", true);

    private WorldGuardFlagRegistrar() {
    }

    public static void register(Plugin plugin, ConfigManager configManager) {
        registerFlag(plugin, configManager, CLIENTCORE_REGEN);
    }

    private static void registerFlag(Plugin plugin, ConfigManager configManager, StateFlag flag) {
        try {
            WorldGuard.getInstance().getFlagRegistry().register(flag);
        } catch (FlagConflictException ignored) {
            Text.log(plugin, configManager, "console.wg-flag-exists");
        }
    }
}

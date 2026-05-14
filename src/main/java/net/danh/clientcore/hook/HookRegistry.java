package net.danh.clientcore.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.clip.placeholderapi.PlaceholderAPI;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.List;

public final class HookRegistry {
    private final Plugin plugin;
    private boolean placeholderApi;
    private boolean worldGuard;
    private boolean mmoItems;
    private boolean mythicMobs;

    public HookRegistry(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.placeholderApi = enabled("placeholderapi") && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.worldGuard = enabled("worldguard") && Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        this.mmoItems = enabled("mmoitems") && Bukkit.getPluginManager().isPluginEnabled("MMOItems");
        this.mythicMobs = enabled("mythicmobs") && Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    private boolean enabled(String key) {
        return plugin.getConfig().getBoolean("hooks." + key, true);
    }

    public String placeholders(Player player, String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return placeholderApi ? PlaceholderAPI.setPlaceholders(player, input) : input;
    }

    public boolean worldGuardFlagAllows(Player player, Location location, String flagName) {
        if (!worldGuard || flagName == null || flagName.isBlank()) {
            return true;
        }
        Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(flagName);
        if (flag == null && WorldGuardFlagRegistrar.CLIENTCORE_REGEN.getName().equalsIgnoreCase(flagName)) {
            flag = WorldGuardFlagRegistrar.CLIENTCORE_REGEN;
        }
        if (!(flag instanceof StateFlag stateFlag)) {
            return true;
        }
        StateFlag.State result = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .createQuery()
                .queryState(BukkitAdapter.adapt(location), WorldGuardPlugin.inst().wrapPlayer(player), stateFlag);
        return result != StateFlag.State.DENY;
    }

    public boolean hasMmoItems() {
        return mmoItems;
    }

    public boolean hasMythicMobs() {
        return mythicMobs;
    }

    public Optional<ItemStack> mmoItem(String typeId, String itemId) {
        if (!mmoItems || typeId == null || itemId == null) {
            return Optional.empty();
        }
        Type type = Type.get(typeId);
        if (type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(MMOItems.plugin.getItem(type, itemId));
    }

    public Optional<Entity> mythicMob(String mobId, Location location, double level) {
        if (!mythicMobs || mobId == null || mobId.isBlank()) {
            return Optional.empty();
        }
        Optional<MythicMob> mob = MythicBukkit.inst().getMobManager().getMythicMob(mobId);
        if (mob.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(io.lumine.mythic.bukkit.BukkitAdapter.adapt(
                mob.get().spawn(io.lumine.mythic.bukkit.BukkitAdapter.adapt(location), level).getEntity()
        ));
    }

    public List<String> mythicMobIds() {
        if (!mythicMobs) {
            return List.of();
        }
        return MythicBukkit.inst().getMobManager().getMobNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<String> mmoItemTypes() {
        if (!mmoItems) {
            return List.of();
        }
        return MMOItems.plugin.getTypes().getAll().stream().map(Type::getId).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<String> mmoItemIds(String typeId) {
        if (!mmoItems || typeId == null || typeId.isBlank()) {
            return List.of();
        }
        Type type = Type.get(typeId);
        if (type == null) {
            return List.of();
        }
        return MMOItems.plugin.getTemplates().getTemplates(type).stream().map(MMOItemTemplate::getId).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}

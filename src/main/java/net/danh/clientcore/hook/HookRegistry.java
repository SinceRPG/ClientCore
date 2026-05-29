package net.danh.clientcore.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import me.clip.placeholderapi.PlaceholderAPI;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.plugin.CitizensHook;
import net.danh.clientcore.hook.plugin.CustomBlockHook;
import net.danh.clientcore.hook.plugin.FancyNpcsHook;
import net.danh.clientcore.hook.plugin.MMOItemsHook;
import net.danh.clientcore.hook.plugin.MythicMobsHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

public final class HookRegistry {
    private final Plugin plugin;
    private final ConfigManager config;
    private boolean placeholderApi;
    private boolean worldGuard;
    private boolean mmoItems;
    private boolean mythicMobs;
    private boolean modelEngine;
    private boolean citizens;
    private boolean fancyNpcs;
    private boolean oraxen;
    private boolean itemsAdder;
    private boolean nexo;
    private boolean craftEngine;

    public HookRegistry(Plugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() {
        this.placeholderApi = enabled("placeholderapi") && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.worldGuard = enabled("worldguard") && Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        this.mmoItems = enabled("mmoitems") && Bukkit.getPluginManager().isPluginEnabled("MMOItems");
        this.mythicMobs = enabled("mythicmobs") && Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
        this.modelEngine = enabled("modelengine") && Bukkit.getPluginManager().isPluginEnabled("ModelEngine");
        this.citizens = enabled("citizens") && Bukkit.getPluginManager().isPluginEnabled("Citizens");
        this.fancyNpcs = enabled("fancynpcs") && Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
        this.oraxen = enabled("oraxen") && Bukkit.getPluginManager().isPluginEnabled("Oraxen");
        this.itemsAdder = enabled("itemsadder") && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
        this.nexo = enabled("nexo") && Bukkit.getPluginManager().isPluginEnabled("Nexo");
        this.craftEngine = enabled("craftengine") && Bukkit.getPluginManager().isPluginEnabled("CraftEngine");
    }

    private boolean enabled(String key) {
        return config.getMain().getBoolean("hooks." + key, true);
    }

    public String placeholders(Player player, String input) {
        if (input == null || input.isBlank()) return "";
        return placeholderApi ? PlaceholderAPI.setPlaceholders(player, input) : input;
    }

    public boolean worldGuardFlagAllows(Player player, Location location, String flagName) {
        if (!worldGuard || flagName == null || flagName.isBlank()) return true;
        Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(flagName);
        if (flag == null && WorldGuardFlagRegistrar.CLIENTCORE_REGEN.getName().equalsIgnoreCase(flagName)) {
            flag = WorldGuardFlagRegistrar.CLIENTCORE_REGEN;
        }
        if (!(flag instanceof StateFlag stateFlag)) return true;
        StateFlag.State result = WorldGuard.getInstance()
                .getPlatform().getRegionContainer().createQuery()
                .queryState(BukkitAdapter.adapt(location), WorldGuardPlugin.inst().wrapPlayer(player), stateFlag);
        return result != StateFlag.State.DENY;
    }

    public boolean hasMmoItems() {
        return mmoItems;
    }

    public boolean hasMythicMobs() {
        return mythicMobs;
    }

    public boolean hasModelEngine() {
        return modelEngine;
    }

    public boolean hasCitizens() {
        return citizens;
    }

    public boolean hasFancyNpcs() {
        return fancyNpcs;
    }

    public boolean hasOraxen() {
        return oraxen;
    }

    public boolean hasItemsAdder() {
        return itemsAdder;
    }

    public boolean hasNexo() {
        return nexo;
    }

    public boolean hasCraftEngine() {
        return craftEngine;
    }

    public String customBlockHookStatus() {
        return "Oraxen: " + status(oraxen)
                + " | ItemsAdder: " + status(itemsAdder)
                + " | Nexo: " + status(nexo)
                + " | CraftEngine: " + status(craftEngine);
    }

    private String status(boolean active) {
        return active ? "active" : "inactive";
    }

    public Optional<ItemStack> mmoItem(String typeId, String itemId) {
        if (!mmoItems || typeId == null || itemId == null) return Optional.empty();
        return MMOItemsHook.getItem(typeId, itemId);
    }

    public Optional<BlockData> customBlockData(String configuredId) {
        return CustomBlockHook.resolve(configuredId, oraxen, itemsAdder, nexo, craftEngine);
    }

    public boolean mmoItemMatches(ItemStack item, String typeId, String itemId) {
        return mmoItems && MMOItemsHook.matches(item, typeId, itemId);
    }

    public Optional<Entity> mythicMob(String mobId, Location location, double level) {
        if (!mythicMobs || mobId == null || mobId.isBlank()) return Optional.empty();
        return MythicMobsHook.spawn(mobId, location, level);
    }

    public void toggleFancyNpcVisibility(Player player, String npcId, boolean visible) {
        if (!hasFancyNpcs()) return;
        FancyNpcsHook.toggleVisibility(plugin, npcId, player, visible);
    }

    public void toggleCitizensNpcVisibility(Player player, String npcId, boolean visible) {
        if (!hasCitizens()) return;
        CitizensHook.toggleVisibility(plugin, npcId, player, visible);
    }

    public List<String> mythicMobIds() {
        if (!mythicMobs) return List.of();
        return MythicMobsHook.getMobIds();
    }

    public List<String> mmoItemTypes() {
        if (!mmoItems) return List.of();
        return MMOItemsHook.getTypes();
    }

    public List<String> mmoItemIds(String typeId) {
        if (!mmoItems || typeId == null || typeId.isBlank()) return List.of();
        return MMOItemsHook.getItems(typeId);
    }
}

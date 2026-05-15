package net.danh.clientcore.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.clip.placeholderapi.PlaceholderAPI;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.danh.clientcore.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class HookRegistry {
    private final Plugin plugin;
    private final ConfigManager config;
    private boolean placeholderApi;
    private boolean worldGuard;
    private boolean mmoItems;
    private boolean mythicMobs;
    private boolean citizens;
    private boolean fancyNpcs;

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
        this.citizens = enabled("citizens") && Bukkit.getPluginManager().isPluginEnabled("Citizens");
        this.fancyNpcs = enabled("fancynpcs") && Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
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

    public boolean hasCitizens() {
        return citizens;
    }

    public boolean hasFancyNpcs() {
        return fancyNpcs;
    }

    public Optional<ItemStack> mmoItem(String typeId, String itemId) {
        if (!mmoItems || typeId == null || itemId == null) return Optional.empty();
        Type type = Type.get(typeId);
        if (type == null) return Optional.empty();
        return Optional.ofNullable(MMOItems.plugin.getItem(type, itemId));
    }

    public Optional<Entity> mythicMob(String mobId, Location location, double level) {
        if (!mythicMobs || mobId == null || mobId.isBlank()) return Optional.empty();
        Optional<MythicMob> mob = MythicBukkit.inst().getMobManager().getMythicMob(mobId);
        if (mob.isEmpty()) return Optional.empty();
        return Optional.ofNullable(io.lumine.mythic.bukkit.BukkitAdapter.adapt(
                mob.get().spawn(io.lumine.mythic.bukkit.BukkitAdapter.adapt(location), level).getEntity()
        ));
    }

    public Object spawnFancyNpc(String name, Location loc, EntityType type, Player viewer) {
        if (!fancyNpcs) return null;
        try {
            UUID npcId = UUID.randomUUID();
            NpcData data = new NpcData(npcId.toString(), viewer.getUniqueId(), loc);
            data.setType(type);
            data.setDisplayName(name);
            Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
            npc.create();
            npc.spawn(viewer);
            return npc;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to spawn FancyNpc: " + t.getMessage());
            return null;
        }
    }

    public void removeFancyNpc(Object npcObject, Player viewer) {
        if (!fancyNpcs || !(npcObject instanceof Npc npc)) return;
        try {
            npc.remove(viewer);
            FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
        } catch (Throwable ignored) {
        }
    }

    public Entity spawnCitizensNpc(String name, Location loc, EntityType type) {
        if (!citizens) return null;
        try {
            NPCRegistry registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
            NPC npc = registry.createNPC(type, name);
            if (npc.spawn(loc)) return npc.getEntity();
            return null;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to spawn Citizens NPC: " + t.getMessage());
            return null;
        }
    }

    public List<String> mythicMobIds() {
        if (!mythicMobs) return List.of();
        return MythicBukkit.inst().getMobManager().getMobNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<String> mmoItemTypes() {
        if (!mmoItems) return List.of();
        return MMOItems.plugin.getTypes().getAll().stream().map(Type::getId).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<String> mmoItemIds(String typeId) {
        if (!mmoItems || typeId == null || typeId.isBlank()) return List.of();
        Type type = Type.get(typeId);
        if (type == null) return List.of();
        return MMOItems.plugin.getTemplates().getTemplates(type).stream().map(MMOItemTemplate::getId).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
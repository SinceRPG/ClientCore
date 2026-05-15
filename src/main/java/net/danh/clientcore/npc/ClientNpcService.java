package net.danh.clientcore.npc;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.util.FoliaScheduler;
import net.danh.clientcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientNpcService implements Listener {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final FoliaScheduler scheduler;
    private final HookRegistry hooks;
    private final ClientPacketService packets;
    private final ConditionEvaluator conditions;
    private final Map<UUID, Map<String, ClientNpc>> activeNpcs = new ConcurrentHashMap<>();
    private List<NpcRule> rules = List.of();
    private boolean enabled;
    private ScheduledTask refreshTask;

    public ClientNpcService(Plugin plugin, ConfigManager configManager, FoliaScheduler scheduler, HookRegistry hooks, ClientPacketService packets, ConditionEvaluator conditions) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
        this.hooks = hooks;
        this.packets = packets;
        this.conditions = conditions;
    }

    public void reload() {
        this.enabled = configManager.getNpcs().getBoolean("client-npcs.enabled", true);
        this.rules = new NpcRuleLoader(plugin, configManager).load();

        if (refreshTask != null) refreshTask.cancel();

        long period = configManager.getNpcs().getLong("client-npcs.refresh-period-ticks", 100L);
        refreshTask = scheduler.globalTimer(20L, period, task -> {
            if (!enabled) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().execute(plugin, () -> refreshFor(player), null, 1L);
            }
        });
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        for (Map.Entry<UUID, Map<String, ClientNpc>> entry : activeNpcs.entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            for (ClientNpc npc : entry.getValue().values()) {
                npc.remove(viewer);
            }
        }
        activeNpcs.clear();
    }

    private void refreshFor(Player player) {
        if (!player.isOnline()) return;
        Map<String, ClientNpc> spawned = activeNpcs.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());

        for (NpcRule rule : rules) {
            boolean passed = conditions.evaluate(player, rule.condition(), rule.conditions()).passed();
            boolean exists = spawned.containsKey(rule.id());

            if (passed && !exists) {
                scheduler.region(rule.location(), () -> {
                    if (rule.location().getWorld() == null) return;
                    ClientNpc npc = spawnAbstractNpc(player, rule);
                    if (npc != null) {
                        spawned.put(rule.id(), npc);
                    }
                });
            } else if (!passed && exists) {
                ClientNpc npc = spawned.remove(rule.id());
                if (npc != null) {
                    npc.remove(player);
                }
            }
        }
    }

    private ClientNpc spawnAbstractNpc(Player viewer, NpcRule rule) {
        String parsedName = hooks.placeholders(viewer, rule.name());

        if ("FANCYNPCS".equalsIgnoreCase(rule.providerType()) && hooks.hasFancyNpcs()) {
            ClientNpc fancy = hooks.spawnFancyNpc(parsedName, rule.location(), rule.entityType(), viewer);
            if (fancy != null) return fancy;
        }

        Entity bukkitEntity = null;
        if ("MYTHICMOBS".equalsIgnoreCase(rule.providerType()) && hooks.hasMythicMobs()) {
            bukkitEntity = hooks.mythicMob(rule.providerId(), rule.location(), 1.0).orElse(null);
        } else if ("CITIZENS".equalsIgnoreCase(rule.providerType()) && hooks.hasCitizens()) {
            bukkitEntity = hooks.spawnCitizensNpc(parsedName, rule.location(), rule.entityType());
        }

        if (bukkitEntity == null) {
            bukkitEntity = rule.location().getWorld().spawnEntity(rule.location(), rule.entityType());
            bukkitEntity.setInvulnerable(true);
            bukkitEntity.setSilent(true);
            bukkitEntity.setGravity(false);
            if (bukkitEntity instanceof LivingEntity le) {
                le.setAI(false);
                le.setRemoveWhenFarAway(false);
            }
            if (!parsedName.isBlank()) {
                bukkitEntity.customName(Text.mm(parsedName));
                bukkitEntity.setCustomNameVisible(true);
            }
        }

        hideFromOthers(bukkitEntity, viewer);
        final Entity finalEntity = bukkitEntity;

        return new ClientNpc() {
            @Override
            public void remove(Player p) {
                finalEntity.getScheduler().execute(plugin, finalEntity::remove, null, 1L);
            }

            @Override
            public Object getHandle() {
                return finalEntity;
            }
        };
    }

    private void hideFromOthers(Entity entity, Player viewer) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                online.hideEntity(plugin, entity);
                packets.destroyEntity(online, entity.getEntityId());
            } else {
                online.showEntity(plugin, entity);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduler.regionLater(event.getPlayer().getLocation(), 20L, task -> refreshFor(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Map<String, ClientNpc> npcs = activeNpcs.remove(event.getPlayer().getUniqueId());
        if (npcs != null) {
            for (ClientNpc npc : npcs.values()) {
                npc.remove(event.getPlayer());
            }
        }
    }
}
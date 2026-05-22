package net.danh.clientcore.block;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.item.ConfigItemBuilder;
import net.danh.clientcore.luck.LuckService;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.util.FoliaScheduler;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockRegenService extends PacketListenerAbstract implements Listener {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final FoliaScheduler scheduler;
    private final HookRegistry hooks;
    private final ClientPacketService packets;
    private final LuckService luck;
    private final ConditionEvaluator conditions;
    private final ConfigItemBuilder itemBuilder;
    private final Map<UUID, Set<String>> regenerating = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Location>> visibleBlocks = new ConcurrentHashMap<>();
    private List<BlockRule> rules = List.of();
    private boolean enabled;
    private int refreshRadius;
    private ScheduledTask refreshTask;
    private long joinDelayTicks;

    public BlockRegenService(Plugin plugin, ConfigManager configManager, FoliaScheduler scheduler, HookRegistry hooks, ClientPacketService packets, LuckService luck) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
        this.hooks = hooks;
        this.packets = packets;
        this.luck = luck;
        this.conditions = new ConditionEvaluator(hooks);
        this.itemBuilder = new ConfigItemBuilder(plugin, hooks);
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void reload() {
        this.enabled = configManager.getBlocks().getBoolean("block-regen.enabled", true);
        this.refreshRadius = Math.max(1, configManager.getBlocks().getInt("block-regen.refresh-radius", 10));
        this.joinDelayTicks = Math.max(1L, configManager.getBlocks().getLong("block-regen.join-delay-ticks", 5L));
        this.rules = new BlockRuleLoader(plugin, configManager.getBlocks()).load();
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        long period = Math.max(5L, configManager.getBlocks().getLong("block-regen.refresh-period-ticks", 20L));
        refreshTask = scheduler.globalTimer(20L, period, task -> {
            if (!enabled) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().execute(plugin, () -> refreshAround(player), null, 1L);
            }
        });
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().execute(plugin, () -> refreshAround(player), null, 1L);
        }
    }

    public void shutdown() {
        regenerating.clear();
        visibleBlocks.clear();
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!enabled || event.getPacketType() != com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_DIGGING)
            return;

        WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
        if (digging.getAction() != DiggingAction.FINISHED_DIGGING && digging.getAction() != DiggingAction.START_DIGGING)
            return;

        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;

        int x = digging.getBlockPosition().getX();
        int y = digging.getBlockPosition().getY();
        int z = digging.getBlockPosition().getZ();

        Optional<BlockMatch> optionalMatch = ruleForLocation(player, x, y, z);
        if (optionalMatch.isEmpty()) return;

        // Bỏ qua START_DIGGING cho khối có thời gian phá, trừ khi ở chế độ sáng tạo
        if (digging.getAction() == DiggingAction.START_DIGGING && player.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        Location loc = optionalMatch.get().rule().location();
        scheduler.region(loc, () -> handleBlockBreak(player, optionalMatch.get()));
    }

    private void handleBlockBreak(Player player, BlockMatch match) {
        BlockRule rule = match.rule();
        BlockVariant variant = match.variant();
        Set<String> active = regenerating.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());

        if (!active.add(rule.id())) {
            return;
        }

        BlockData cooldownData;
        if ("ORIGINAL".equalsIgnoreCase(variant.cooldownBlock())) {
            cooldownData = rule.location().getWorld().getBlockAt(rule.location()).getBlockData();
        } else {
            Material mat = Material.matchMaterial(variant.cooldownBlock());
            cooldownData = mat != null ? Bukkit.createBlockData(mat) : Bukkit.createBlockData(Material.AIR);
        }
        packets.sendBlock(player, rule.location(), cooldownData);

        giveDrops(player, variant.drops());

        scheduler.regionLater(rule.location(), variant.regenTicks(), task -> {
            if (player.isOnline()) {
                BlockData finalData;
                if ("ORIGINAL".equalsIgnoreCase(variant.readyBlock())) {
                    finalData = rule.location().getWorld().getBlockAt(rule.location()).getBlockData();
                } else {
                    Material mat = Material.matchMaterial(variant.readyBlock());
                    finalData = mat != null ? Bukkit.createBlockData(mat) : Bukkit.createBlockData(Material.AIR);
                }
                packets.sendBlock(player, rule.location(), finalData);
            }
            active.remove(rule.id());
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduler.regionLater(player.getLocation(), joinDelayTicks, task -> refreshAround(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        regenerating.remove(event.getPlayer().getUniqueId());
        visibleBlocks.remove(event.getPlayer().getUniqueId());
    }

    public int ruleCount() {
        return rules.size();
    }

    public List<String> ruleIds() {
        return rules.stream().map(BlockRule::id).toList();
    }

    public void refreshAround(Player player) {
        if (!player.isOnline()) return;
        if (!enabled) {
            restoreVisible(player);
            return;
        }

        Map<String, Location> visible = visibleBlocks.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        Set<String> activeRegen = regenerating.getOrDefault(player.getUniqueId(), Set.of());

        for (BlockRule rule : rules) {
            Location loc = rule.location();
            if (loc.getWorld() != player.getWorld() || !loc.isChunkLoaded()) continue;

            double dist = player.getLocation().distanceSquared(loc);
            boolean inRange = dist <= (refreshRadius * refreshRadius);

            boolean passedConditions = conditions.evaluate(player, rule.condition(), rule.conditions()).passed();
            boolean isRegenerating = activeRegen.contains(rule.id());

            boolean shouldBeVisible = inRange && passedConditions;
            boolean isVisible = visible.containsKey(rule.id());

            if (shouldBeVisible) {
                BlockVariant variant = chooseVariant(player, rule, conditions.evaluate(player, rule.condition(), rule.conditions()).passedOptionalIds());
                String blockMaterial = isRegenerating ? variant.cooldownBlock() : variant.readyBlock();
                BlockData data;
                if ("ORIGINAL".equalsIgnoreCase(blockMaterial)) {
                    data = loc.getWorld().getBlockAt(loc).getBlockData();
                } else {
                    Material mat = Material.matchMaterial(blockMaterial);
                    data = mat != null ? Bukkit.createBlockData(mat) : Bukkit.createBlockData(Material.AIR);
                }

                // Gửi packet nếu chưa visible hoặc nếu trạng thái block bị lỗi đồng bộ
                packets.sendBlock(player, loc, data);
                if (!isVisible) visible.put(rule.id(), loc);

            } else if (!shouldBeVisible && isVisible) {
                visible.remove(rule.id());
                packets.sendBlock(player, loc, loc.getWorld().getBlockAt(loc).getBlockData());
            }
        }
    }

    private void restoreVisible(Player player) {
        Map<String, Location> visible = visibleBlocks.remove(player.getUniqueId());
        if (visible == null || visible.isEmpty()) return;
        for (Location loc : visible.values()) {
            World world = loc.getWorld();
            if (world == null || !loc.isChunkLoaded()) continue;
            packets.sendBlock(player, loc, world.getBlockAt(loc).getBlockData());
        }
    }

    private Optional<BlockMatch> ruleForLocation(Player player, int x, int y, int z) {
        for (BlockRule rule : rules) {
            Location loc = rule.location();
            if (loc.getBlockX() == x && loc.getBlockY() == y && loc.getBlockZ() == z && loc.getWorld().equals(player.getWorld())) {
                if (!hooks.worldGuardFlagAllows(player, loc, rule.worldGuardFlag())) continue;
                ConditionEvaluator.Evaluation evaluation = conditions.evaluate(player, rule.condition(), rule.conditions());
                if (!evaluation.passed()) continue;
                return Optional.of(new BlockMatch(rule, chooseVariant(player, rule, evaluation.passedOptionalIds())));
            }
        }
        return Optional.empty();
    }

    private BlockVariant chooseVariant(Player player, BlockRule rule, Set<String> passedConditionIds) {
        List<BlockVariant> eligible = rule.variants().stream()
                .filter(variant -> variant.requiredConditionIds().isEmpty() || passedConditionIds.containsAll(variant.requiredConditionIds()))
                .toList();
        if (eligible.isEmpty())
            eligible = rule.variants().stream().filter(variant -> variant.requiredConditionIds().isEmpty()).toList();
        if (eligible.isEmpty()) eligible = rule.variants();
        if (eligible.size() == 1) return eligible.getFirst();

        double playerLuck = luck.luck(player);
        double total = 0.0D;
        for (BlockVariant variant : eligible) {
            total += adjustedWeight(variant.weight(), variant.rare(), variant.luckMultiplier(), playerLuck);
        }
        if (total <= 0.0D) return eligible.getFirst();

        long seed = player.getUniqueId().getMostSignificantBits() ^ rule.location().hashCode() ^ rule.id().hashCode();
        double roll = new Random(seed).nextDouble(total);
        double cursor = 0.0D;
        for (BlockVariant variant : eligible) {
            cursor += adjustedWeight(variant.weight(), variant.rare(), variant.luckMultiplier(), playerLuck);
            if (roll <= cursor) return variant;
        }
        return eligible.getLast();
    }

    private double adjustedWeight(double base, boolean rare, double multiplier, double playerLuck) {
        if (!rare || playerLuck <= 0.0D) return base;
        double maxBonus = Math.max(0.0D, configManager.getMain().getDouble("luck.max-rare-weight-bonus-percent", 300.0D)) / 100.0D;
        double bonus = Math.min(maxBonus, playerLuck * Math.max(0.0D, multiplier) / 100.0D);
        return base * (1.0D + bonus);
    }

    private void giveDrops(Player player, List<ConfigurationSection> drops) {
        List<ItemStack> items = new ArrayList<>();
        for (ConfigurationSection drop : drops) {
            String type = drop.getString("type", "vanilla");
            if ("vanilla".equalsIgnoreCase(type)) {
                items.add(itemBuilder.build(player, drop));
            } else if ("mmoitems".equalsIgnoreCase(type) && hooks.hasMmoItems()) {
                hooks.mmoItem(drop.getString("mmo-type"), drop.getString("mmo-id"))
                        .ifPresentOrElse(items::add, () -> {
                            ItemStack fallback = itemBuilder.build(player, drop);
                            if (!fallback.isEmpty()) {
                                items.add(fallback);
                            }
                        });
            }
        }
        if (!items.isEmpty()) player.give(items);
    }

    private record BlockMatch(BlockRule rule, BlockVariant variant) {
    }
}
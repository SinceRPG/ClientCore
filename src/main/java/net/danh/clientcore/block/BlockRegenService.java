package net.danh.clientcore.block;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.item.ConfigItemBuilder;
import net.danh.clientcore.luck.LuckService;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockRegenService implements Listener {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final FoliaScheduler scheduler;
    private final HookRegistry hooks;
    private final ClientPacketService packets;
    private final LuckService luck;
    private final ConditionEvaluator conditions;
    private final ConfigItemBuilder itemBuilder;
    private final Map<UUID, Set<BlockPos>> regenerating = new ConcurrentHashMap<>();
    private final Map<UUID, Map<BlockPos, BlockData>> visibleBlocks = new ConcurrentHashMap<>();
    private final Set<BlockDisplay> activeDisplays = ConcurrentHashMap.newKeySet();
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
        for (BlockDisplay display : activeDisplays) {
            if (plugin.isEnabled()) {
                display.getScheduler().execute(plugin, display::remove, null, 1L);
            } else {
                try {
                    display.remove();
                } catch (Exception ignored) {
                }
            }
        }
        activeDisplays.clear();
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Optional<BlockMatch> optionalMatch = ruleFor(player, block);
        if (optionalMatch.isEmpty()) return;

        BlockMatch match = optionalMatch.get();
        BlockRule rule = match.rule();
        BlockVariant variant = match.variant();
        BlockPos pos = BlockPos.of(block);
        Set<BlockPos> active = regenerating.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        if (!active.add(pos)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);
        BlockData cooldownData;
        if ("ORIGINAL".equalsIgnoreCase(variant.cooldownBlock())) {
            cooldownData = block.getBlockData();
        } else {
            Material mat = Material.matchMaterial(variant.cooldownBlock());
            cooldownData = mat != null ? Bukkit.createBlockData(mat) : Bukkit.createBlockData(Material.AIR);
        }
        packets.sendBlock(player, block.getLocation(), cooldownData);
        visibleBlocks.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(pos, cooldownData);

        giveDrops(player, variant.drops());

        Location location = block.getLocation();
        if (rule.animation()) {
            BlockData readyData;
            if ("ORIGINAL".equalsIgnoreCase(variant.readyBlock())) {
                readyData = block.getBlockData();
            } else {
                Material mat = Material.matchMaterial(variant.readyBlock());
                readyData = mat != null ? Bukkit.createBlockData(mat) : block.getBlockData();
            }
            animate(player, location, readyData, rule.frames(), variant.regenTicks(), rule.viewRange());
        }

        scheduler.regionLater(location, variant.regenTicks(), task -> {
            if (player.isOnline()) {
                BlockData finalData;
                if ("ORIGINAL".equalsIgnoreCase(variant.readyBlock())) {
                    finalData = block.getBlockData();
                    packets.sendBlock(player, location, finalData);
                    Map<BlockPos, BlockData> visible = visibleBlocks.get(player.getUniqueId());
                    if (visible != null) visible.remove(pos);
                } else {
                    Material mat = Material.matchMaterial(variant.readyBlock());
                    finalData = mat != null ? Bukkit.createBlockData(mat) : block.getBlockData();
                    packets.sendBlock(player, location, finalData);
                    visibleBlocks.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(pos, finalData);
                }
            }
            active.remove(pos);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (BlockDisplay display : activeDisplays) {
            player.hideEntity(plugin, display);
            packets.destroyEntity(player, display.getEntityId());
        }
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
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null || !center.isChunkLoaded()) return;

        Map<BlockPos, BlockData> desired = new HashMap<>();
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();
        Set<BlockPos> activeRegen = regenerating.getOrDefault(player.getUniqueId(), Set.of());

        for (int x = baseX - refreshRadius; x <= baseX + refreshRadius; x++) {
            // An toàn cho Folia: Phải check chunk load trước khi lấy block
            if (!world.isChunkLoaded(x >> 4, baseZ >> 4)) continue;
            for (int z = baseZ - refreshRadius; z <= baseZ + refreshRadius; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = Math.max(world.getMinHeight(), baseY - refreshRadius); y <= Math.min(world.getMaxHeight() - 1, baseY + refreshRadius); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    ruleFor(player, block).ifPresent(match -> {
                        BlockPos pos = BlockPos.of(block);
                        boolean isRegenerating = activeRegen.contains(pos);
                        String blockMaterial = isRegenerating ? match.variant().cooldownBlock() : match.variant().readyBlock();

                        if (!"ORIGINAL".equalsIgnoreCase(blockMaterial)) {
                            Material mat = Material.matchMaterial(blockMaterial);
                            if (mat != null) {
                                desired.put(pos, Bukkit.createBlockData(mat));
                            }
                        }
                    });
                }
            }
        }
        Map<BlockPos, BlockData> visible = visibleBlocks.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        Set<BlockPos> restore = new HashSet<>(visible.keySet());
        restore.removeAll(desired.keySet());
        restore.removeAll(activeRegen);
        for (BlockPos pos : restore) {
            World posWorld = Bukkit.getWorld(pos.world());
            if (posWorld == null || !posWorld.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                visible.remove(pos);
                continue;
            }
            Location location = pos.blockLocation(posWorld);
            packets.sendBlock(player, location, posWorld.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData());
            visible.remove(pos);
        }
        for (Map.Entry<BlockPos, BlockData> entry : desired.entrySet()) {
            BlockData old = visible.get(entry.getKey());
            if (old != null && old.getAsString().equals(entry.getValue().getAsString())) {
                continue;
            }
            Location location = entry.getKey().blockLocation(world);
            packets.sendBlock(player, location, entry.getValue());
            visible.put(entry.getKey(), entry.getValue());
        }
    }

    private void restoreVisible(Player player) {
        Map<BlockPos, BlockData> visible = visibleBlocks.remove(player.getUniqueId());
        if (visible == null || visible.isEmpty()) return;
        for (BlockPos pos : visible.keySet()) {
            World world = Bukkit.getWorld(pos.world());
            if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) continue;
            packets.sendBlock(player, pos.blockLocation(world), world.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData());
        }
    }

    private Optional<BlockMatch> ruleFor(Player player, Block block) {
        String worldName = block.getWorld().getName().toLowerCase(Locale.ROOT);
        for (BlockRule rule : rules) {
            if (!rule.worlds().isEmpty() && !rule.worlds().contains(worldName)) continue;
            if (!rule.sourceBlocks().isEmpty() && !rule.sourceBlocks().contains(block.getType())) continue;
            if (!hooks.worldGuardFlagAllows(player, block.getLocation(), rule.worldGuardFlag())) continue;

            ConditionEvaluator.Evaluation evaluation = conditions.evaluate(player, rule.condition(), rule.conditions());
            if (!evaluation.passed()) continue;
            return Optional.of(new BlockMatch(rule, chooseVariant(player, rule, BlockPos.of(block), evaluation.passedOptionalIds())));
        }
        return Optional.empty();
    }

    private BlockVariant chooseVariant(Player player, BlockRule rule, BlockPos pos, Set<String> passedConditionIds) {
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

        long seed = player.getUniqueId().getMostSignificantBits() ^ pos.hashCode() ^ rule.id().hashCode();
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

    private void animate(Player viewer, Location blockLocation, BlockData blockData, int frames, int totalTicks, float viewRange) {
        Location spawnLocation = blockLocation.clone().add(0.5D, 0.5D, 0.5D);
        World world = spawnLocation.getWorld();
        if (world == null) return;
        BlockDisplay display = (BlockDisplay) world.spawnEntity(spawnLocation, EntityType.BLOCK_DISPLAY);
        activeDisplays.add(display);
        display.setBlock(blockData);
        display.setViewRange(viewRange);
        display.setVisibleByDefault(false);
        viewer.showEntity(plugin, display);
        int period = Math.max(1, totalTicks / frames);
        scheduler.regionTimer(spawnLocation, 1L, period, new java.util.function.Consumer<>() {
            private int frame;

            @Override
            public void accept(ScheduledTask task) {
                if (!viewer.isOnline() || display.isDead() || frame >= frames) {
                    display.remove();
                    activeDisplays.remove(display);
                    task.cancel();
                    return;
                }
                float scale = Math.min(1.0F, (frame + 1.0F) / frames);
                float offset = -0.5F * scale;
                display.setTransformation(new Transformation(
                        new Vector3f(offset, offset, offset),
                        new Quaternionf(),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()
                ));
                frame++;
            }
        });
    }

    private record BlockMatch(BlockRule rule, BlockVariant variant) {
    }
}
package net.danh.clientcore.block;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.hook.plugin.CustomBlockHook;
import net.danh.clientcore.item.ConfigItemBuilder;
import net.danh.clientcore.luck.LuckService;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.util.CompatTask;
import net.danh.clientcore.util.FoliaScheduler;
import net.danh.clientcore.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
    private final ClientBlockSupportService support;
    private final MiningVisualService miningVisuals;
    private final ConditionEvaluator conditions;
    private final ConfigItemBuilder itemBuilder;
    private final Map<UUID, Set<String>> regenerating = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Location>> visibleBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, MiningSession> miningSessions = new ConcurrentHashMap<>();
    private final Map<UUID, VanillaMiningSession> vanillaMiningSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, FarmingState>> farmingStates = new ConcurrentHashMap<>();
    private List<BlockRule> rules = List.of();
    private Map<Material, VanillaBlockMiningRule> vanillaMiningRules = Map.of();
    private boolean enabled;
    private boolean vanillaMiningEnabled;
    private int refreshRadius;
    private CompatTask refreshTask;
    private long joinDelayTicks;

    public BlockRegenService(Plugin plugin, ConfigManager configManager, FoliaScheduler scheduler, HookRegistry hooks, ClientPacketService packets, LuckService luck, ClientBlockSupportService support) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
        this.hooks = hooks;
        this.packets = packets;
        this.luck = luck;
        this.support = support;
        this.miningVisuals = new MiningVisualService(plugin, scheduler);
        this.conditions = new ConditionEvaluator(hooks);
        this.itemBuilder = new ConfigItemBuilder(plugin, hooks);
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void reload() {
        this.enabled = configManager.getBlocks().getBoolean("block-regen.enabled", true);
        this.refreshRadius = Math.max(1, configManager.getBlocks().getInt("block-regen.refresh-radius", 10));
        this.joinDelayTicks = Math.max(1L, configManager.getBlocks().getLong("block-regen.join-delay-ticks", 5L));
        BlockRuleLoader loader = new BlockRuleLoader(plugin, configManager.getBlocks());
        this.rules = loader.load();
        this.vanillaMiningEnabled = configManager.getBlocks().getBoolean("vanilla-mining.enabled", false);
        this.vanillaMiningRules = loader.loadVanillaMiningRules();
        warnUnresolvedActiveBlocks();
        cancelAllMining();
        cancelAllVanillaMining();
        cancelAllFarmingGrowth();
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        long period = Math.max(5L, configManager.getBlocks().getLong("block-regen.refresh-period-ticks", 20L));
        refreshTask = scheduler.globalTimer(20L, period, task -> {
            if (!enabled) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.entity(player, () -> refreshAround(player));
            }
        });
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.entity(player, () -> refreshAround(player));
        }
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreVisible(player);
        }
        regenerating.clear();
        visibleBlocks.clear();
        cancelAllMining();
        cancelAllVanillaMining();
        cancelAllFarmingGrowth();
        miningVisuals.clearAll();
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void warnUnresolvedActiveBlocks() {
        for (BlockRule rule : rules) {
            for (BlockVariant variant : rule.variants()) {
                BlockMiningConfig mining = variant.mining();
                if (!mining.enabled()) {
                    continue;
                }
                String activeBlock = mining.activeBlock();
                if (!CustomBlockHook.hasKnownProviderPrefix(activeBlock)) {
                    continue;
                }
                Optional<BlockData> resolved = hooks.customBlockData(activeBlock);
                if (resolved.isPresent() && !resolved.get().getMaterial().isAir()) {
                    continue;
                }
                Text.warn(plugin, configManager, "console.custom-block-active-unresolved",
                        "{rule}", rule.id(),
                        "{variant}", variant.id(),
                        "{id}", activeBlock == null ? "" : activeBlock,
                        "{visual}", mining.visualMode(),
                        "{provider}", CustomBlockHook.providerName(activeBlock),
                        "{hooks}", hooks.customBlockHookStatus());
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!enabled)
            return;

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement placement = new WrapperPlayClientPlayerBlockPlacement(event);
            Player player = (Player) event.getPlayer();
            if (player == null || !player.isOnline()) return;

            int x = placement.getBlockPosition().getX();
            int y = placement.getBlockPosition().getY();
            int z = placement.getBlockPosition().getZ();
            if (!isVisibleCoordinate(player.getUniqueId(), x, y, z)) return;

            event.setCancelled(true);
            scheduler.entity(player, () -> handleUsePacket(player, x, y, z));
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING)
            return;

        WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
        if (digging.getAction() != DiggingAction.FINISHED_DIGGING
                && digging.getAction() != DiggingAction.START_DIGGING
                && digging.getAction() != DiggingAction.CANCELLED_DIGGING)
            return;

        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;

        int x = digging.getBlockPosition().getX();
        int y = digging.getBlockPosition().getY();
        int z = digging.getBlockPosition().getZ();
        DiggingAction action = digging.getAction();
        if (isVisibleCoordinate(player.getUniqueId(), x, y, z) && hasCustomMiningAt(player, x, y, z)) {
            event.setCancelled(true);
        }

        scheduler.entity(player, () -> handleDigPacket(player, action, x, y, z));
    }

    private void handleDigPacket(Player player, DiggingAction action, int x, int y, int z) {
        if (!player.isOnline()) return;
        Optional<BlockMatch> optionalMatch = ruleForLocation(player, x, y, z);
        if (optionalMatch.isEmpty()) {
            if (action == DiggingAction.CANCELLED_DIGGING) {
                cancelVanillaMining(player, x, y, z, true);
            }
            return;
        }
        BlockMatch match = optionalMatch.get();
        if (match.variant().mining().enabled()) {
            if (action == DiggingAction.START_DIGGING) {
                startCustomMining(player, match);
            } else if (action == DiggingAction.CANCELLED_DIGGING) {
                cancelMining(player, true);
                cancelVanillaMining(player, x, y, z, true);
                sendCurrentVisualBlock(player, match);
            } else if (action == DiggingAction.FINISHED_DIGGING) {
                acknowledgeCurrentVisualDig(player, match, action, false);
                sendCurrentVisualBlock(player, match);
            }
            return;
        }

        // START_DIGGING is ignored for survival players so normal client break timing is preserved.
        if (action == DiggingAction.START_DIGGING && player.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        Location loc = match.rule().location();
        scheduler.region(loc, () -> handleBlockBreak(player, match, match.variant().drops()));
    }

    private Optional<VanillaBlockMiningRule> vanillaRuleFor(Player player, Block block) {
        if (!enabled || !vanillaMiningEnabled || block == null || block.getType().isAir()) {
            return Optional.empty();
        }
        VanillaBlockMiningRule rule = vanillaMiningRules.get(block.getType());
        if (rule == null) {
            return Optional.empty();
        }
        if (rule.mining().tools().isEmpty() && rule.mining().defaultTimeTicks() <= 0) {
            return Optional.empty();
        }
        if (rule.worldGuardFlag() != null
                && !rule.worldGuardFlag().isBlank()
                && !hooks.worldGuardFlagAllows(player, block.getLocation(), rule.worldGuardFlag())) {
            return Optional.empty();
        }
        if (!conditions.evaluate(player, rule.condition(), rule.conditions()).passed()) {
            return Optional.empty();
        }
        return Optional.of(rule);
    }

    private void startVanillaMining(Player player, Block block, VanillaBlockMiningRule rule) {
        Optional<BlockToolRule> tool = matchingTool(player, rule.mining());
        debugToolMatch(player, "vanilla-mining", rule.material().name(), rule.mining().tools(), tool);
        if (tool.isEmpty() && !rule.mining().tools().isEmpty()) {
            return;
        }

        VanillaMiningSession current = vanillaMiningSessions.get(player.getUniqueId());
        Location location = block.getLocation();
        if (current != null && sameBlock(current.location(), location)) {
            return;
        }
        cancelVanillaMining(player, true);

        int timeTicks = tool.map(BlockToolRule::timeTicks).orElse(rule.mining().defaultTimeTicks());
        if (timeTicks <= 0) {
            timeTicks = 1;
        }
        String dropSource = dropSource(tool.orElse(null), rule.drops(), true);
        List<ConfigurationSection> drops = tool.filter(toolRule -> !toolRule.drops().isEmpty())
                .map(BlockToolRule::drops)
                .orElse(rule.drops());
        debugDrops(player, "vanilla-mining", rule.material().name(), dropSource);
        int entityId = Objects.hash(player.getUniqueId(), "vanilla", location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        VanillaMiningSession session = new VanillaMiningSession(location, rule, tool.orElse(null), entityId, drops, timeTicks, 0, -1, null);
        vanillaMiningSessions.put(player.getUniqueId(), session);
        CompatTask task = scheduler.regionTimer(location, 1L, 1L, ignored -> tickVanillaMining(player));
        VanillaMiningSession stored = vanillaMiningSessions.get(player.getUniqueId());
        if (stored != null && sameBlock(stored.location(), location)) {
            vanillaMiningSessions.put(player.getUniqueId(), stored.withTask(task));
        } else {
            task.cancel();
        }
    }

    private void tickVanillaMining(Player player) {
        VanillaMiningSession session = vanillaMiningSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Location location = session.location();
        Block block = location.getBlock();
        if (!player.isOnline()
                || player.getWorld() != location.getWorld()
                || player.getLocation().distanceSquared(location) > 36.0D
                || block.getType() != session.rule().material()
                || (session.tool() != null && !toolMatches(player.getInventory().getItemInMainHand(), session.tool()))) {
            cancelVanillaMining(player, true);
            return;
        }

        int elapsed = session.elapsedTicks() + 1;
        int progress = Math.min(100, (int) Math.floor(elapsed * 100.0D / session.timeTicks()));
        miningVisuals.updateProgress(player, progress, session.rule().mining().feedback());
        int stage = Math.min(9, (int) Math.floor((elapsed * 10.0D) / session.timeTicks()));
        if (stage != session.stage()) {
            packets.sendBlockBreakAnimation(player, location, session.animationEntityId(), stage);
        }
        sendVanillaMiningFeedback(player, session, elapsed, stage);

        if (elapsed >= session.timeTicks()) {
            vanillaMiningSessions.remove(player.getUniqueId());
            if (session.task() != null) {
                session.task().cancel();
            }
            packets.sendBlockBreakAnimation(player, location, session.animationEntityId(), -1);
            miningVisuals.clear(player);
            breakVanillaBlock(player, block, session);
            return;
        }

        vanillaMiningSessions.put(player.getUniqueId(), session.withProgress(elapsed, stage));
    }

    private void sendVanillaMiningFeedback(Player player, VanillaMiningSession session, int elapsed, int stage) {
        BlockMiningFeedback feedback = session.rule().mining().feedback();
        int progress = Math.min(100, (int) Math.floor(elapsed * 100.0D / session.timeTicks()));
        boolean intervalTick = elapsed == 1 || elapsed % feedback.intervalTicks() == 0;

        if (feedback.actionBar() && intervalTick) {
            player.sendActionBar(Text.mm(feedback.message().replace("{progress}", String.valueOf(progress))));
        }

        Location center = session.location().toBlockLocation().add(0.5D, 0.5D, 0.5D);
        if (feedback.particles() && intervalTick) {
            player.spawnParticle(Particle.CRIT, center, 4, 0.28D, 0.28D, 0.28D, 0.01D);
            if (progress >= 75) {
                player.spawnParticle(Particle.DUST, center, 2, 0.22D, 0.22D, 0.22D, 0.0D, new Particle.DustOptions(Color.GRAY, 0.8F));
            }
        }

        if (feedback.sounds() && (elapsed == 1 || stage != session.stage())) {
            player.playSound(center, blockHitSound(session.rule().material()), 0.35F, 0.75F + (stage * 0.035F));
        }
    }

    private Sound blockHitSound(Material material) {
        try {
            return material.createBlockData().getSoundGroup().getHitSound();
        } catch (IllegalArgumentException ignored) {
            return Sound.BLOCK_STONE_HIT;
        }
    }

    private void breakVanillaBlock(Player player, Block block, VanillaMiningSession session) {
        if (block.getType() != session.rule().material()) {
            return;
        }
        player.playSound(block.getLocation(), block.getBlockData().getSoundGroup().getBreakSound(), 1.0F, 1.0F);
        if (session.drops().isEmpty()) {
            debug(player, "vanilla-mining " + session.rule().material().name() + " using natural break fallback drops");
            block.breakNaturally(player.getInventory().getItemInMainHand());
            return;
        }
        block.setType(Material.AIR);
        scheduler.entity(player, () -> giveDrops(player, session.drops()));
    }

    private void cancelVanillaMining(Player player, int x, int y, int z, boolean clearAnimation) {
        VanillaMiningSession session = vanillaMiningSessions.get(player.getUniqueId());
        if (session == null || session.location().getBlockX() != x || session.location().getBlockY() != y || session.location().getBlockZ() != z) {
            return;
        }
        cancelVanillaMining(player, clearAnimation);
    }

    private void cancelVanillaMining(Player player, boolean clearAnimation) {
        VanillaMiningSession session = vanillaMiningSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.task() != null) {
            session.task().cancel();
        }
        miningVisuals.clear(player);
        if (clearAnimation) {
            packets.sendBlockBreakAnimation(player, session.location(), session.animationEntityId(), -1);
        }
    }

    private void cancelAllVanillaMining() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            cancelVanillaMining(player, true);
        }
        vanillaMiningSessions.clear();
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private void handleBlockBreak(Player player, BlockMatch match, List<ConfigurationSection> drops) {
        BlockRule rule = match.rule();
        BlockVariant variant = match.variant();
        Set<String> active = regenerating.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());

        if (!active.add(rule.id())) {
            return;
        }

        BlockData cooldownData;
        miningVisuals.clear(player);
        if ("ORIGINAL".equalsIgnoreCase(variant.cooldownBlock())) {
            cooldownData = rule.location().getWorld().getBlockAt(rule.location()).getBlockData();
        } else {
            Material mat = Material.matchMaterial(variant.cooldownBlock());
            cooldownData = mat != null ? Bukkit.createBlockData(mat) : Bukkit.createBlockData(Material.AIR);
        }
        sendBlockData(player, rule.location(), cooldownData);

        scheduler.entity(player, () -> giveDrops(player, drops));

        scheduler.regionLater(rule.location(), variant.regenTicks(), task -> {
            if (player.isOnline()) {
                BlockData finalData;
                if ("ORIGINAL".equalsIgnoreCase(variant.readyBlock())) {
                    finalData = rule.location().getWorld().getBlockAt(rule.location()).getBlockData();
                } else {
                    Material mat = Material.matchMaterial(variant.readyBlock());
                    finalData = mat != null ? Bukkit.createBlockData(mat) : Bukkit.createBlockData(Material.AIR);
                }
                sendBlockData(player, rule.location(), finalData);
            }
            active.remove(rule.id());
        });
    }

    private void handleUsePacket(Player player, int x, int y, int z) {
        Optional<BlockMatch> match = ruleForLocation(player, x, y, z);
        if (match.isEmpty() || !isVisible(player, match.get().rule())) {
            return;
        }
        if (match.get().variant().farming().enabled()) {
            handleFarming(player, match.get());
            return;
        }
        sendCurrentVisualBlock(player, match.get());
    }

    private void handleFarming(Player player, BlockMatch match) {
        Optional<BlockToolRule> tool = matchingTool(player, match.variant().farming().tools());
        debugToolMatch(player, "farming", match.rule().id() + "/" + match.variant().id(), match.variant().farming().tools(), tool);
        if (tool.isEmpty() && !match.variant().farming().tools().isEmpty()) {
            sendCurrentVisualBlock(player, match);
            return;
        }

        List<ConfigurationSection> stageDrops = farmingStageDrops(player, match);
        String dropSource = dropSource(tool.orElse(null), stageDrops.isEmpty() ? match.variant().drops() : stageDrops, false);
        List<ConfigurationSection> drops = tool.filter(rule -> !rule.drops().isEmpty())
                .map(BlockToolRule::drops)
                .orElse(stageDrops.isEmpty() ? match.variant().drops() : stageDrops);
        debugDrops(player, "farming", match.rule().id() + "/" + match.variant().id(), dropSource);
        cancelMining(player, true);
        if (match.variant().farming().stages().isEmpty()) {
            scheduler.region(match.rule().location(), () -> handleBlockBreak(player, match, drops));
            return;
        }
        scheduler.region(match.rule().location(), () -> {
            miningVisuals.clear(player);
            sendBlockData(player, match.rule().location(), farmingStageBlockData(match, 0));
            scheduler.entity(player, () -> giveDrops(player, drops));
            startFarmingGrowth(player, match);
        });
    }

    private List<ConfigurationSection> farmingStageDrops(Player player, BlockMatch match) {
        List<BlockFarmingStage> stages = match.variant().farming().stages();
        if (stages.isEmpty()) {
            return List.of();
        }
        FarmingState state = farmingState(player, match.rule().id());
        int index = state == null ? stages.size() - 1 : Math.max(0, Math.min(state.stageIndex(), stages.size() - 1));
        return stages.get(index).drops();
    }

    private void startFarmingGrowth(Player player, BlockMatch match) {
        List<BlockFarmingStage> stages = match.variant().farming().stages();
        if (stages.isEmpty()) {
            return;
        }
        cancelFarmingGrowth(player.getUniqueId(), match.rule().id());
        FarmingState state = new FarmingState(0, new ArrayList<>());
        farmingStates.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(match.rule().id(), state);
        scheduleFarmingStage(player, match, 1);
    }

    private void scheduleFarmingStage(Player player, BlockMatch match, int nextIndex) {
        List<BlockFarmingStage> stages = match.variant().farming().stages();
        if (nextIndex >= stages.size()) {
            return;
        }
        BlockFarmingStage next = stages.get(nextIndex);
        CompatTask task = scheduler.regionLater(match.rule().location(), next.afterTicks(), ignored -> {
            FarmingState current = farmingState(player, match.rule().id());
            if (current == null || !player.isOnline()) {
                return;
            }
            farmingStates.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>())
                    .put(match.rule().id(), current.withStage(nextIndex));
            if (isVisible(player, match.rule())) {
                sendBlockData(player, match.rule().location(), farmingStageBlockData(match, nextIndex));
            }
            scheduleFarmingStage(player, match, nextIndex + 1);
        });
        FarmingState current = farmingState(player, match.rule().id());
        if (current != null) {
            current.tasks().add(task);
        } else {
            task.cancel();
        }
    }

    private BlockData farmingStageBlockData(BlockMatch match, int stageIndex) {
        List<BlockFarmingStage> stages = match.variant().farming().stages();
        if (stages.isEmpty()) {
            return configuredBlockData(match.variant().cooldownBlock());
        }
        String block = stages.get(Math.max(0, Math.min(stageIndex, stages.size() - 1))).block();
        if (block == null || block.isBlank()) {
            block = stageIndex == stages.size() - 1 ? match.variant().readyBlock() : match.variant().cooldownBlock();
        }
        return configuredBlockData(block);
    }

    private FarmingState farmingState(Player player, String ruleId) {
        Map<String, FarmingState> states = farmingStates.get(player.getUniqueId());
        return states == null ? null : states.get(ruleId);
    }

    private void cancelFarmingGrowth(UUID playerId, String ruleId) {
        Map<String, FarmingState> states = farmingStates.get(playerId);
        if (states == null) {
            return;
        }
        FarmingState state = states.remove(ruleId);
        if (state != null) {
            state.tasks().forEach(CompatTask::cancel);
        }
        if (states.isEmpty()) {
            farmingStates.remove(playerId);
        }
    }

    private void startCustomMining(Player player, BlockMatch match) {
        if (regenerating.getOrDefault(player.getUniqueId(), Set.of()).contains(match.rule().id())) {
            sendCurrentVisualBlock(player, match);
            return;
        }

        Optional<BlockToolRule> tool = matchingTool(player, match.variant().mining());
        debugToolMatch(player, "block-regen-mining", match.rule().id() + "/" + match.variant().id(), match.variant().mining().tools(), tool);
        if (tool.isEmpty() && !match.variant().mining().tools().isEmpty()) {
            sendCurrentVisualBlock(player, match);
            clearBreakAnimation(player, match.rule());
            return;
        }

        MiningSession current = miningSessions.get(player.getUniqueId());
        if (current != null && current.ruleId().equals(match.rule().id())) {
            if (current.task() == null) {
                resumeCustomMining(player, match, current);
            }
            return;
        }
        cancelMining(player, true);

        int timeTicks = tool.map(BlockToolRule::timeTicks).orElse(match.variant().mining().defaultTimeTicks());
        if (timeTicks <= 0) {
            timeTicks = 1;
        }
        String dropSource = dropSource(tool.orElse(null), match.variant().drops(), false);
        List<ConfigurationSection> drops = tool.filter(rule -> !rule.drops().isEmpty())
                .map(BlockToolRule::drops)
                .orElse(match.variant().drops());
        debugDrops(player, "block-regen-mining", match.rule().id() + "/" + match.variant().id(), dropSource);
        int entityId = breakAnimationEntityId(player, match.rule());
        MiningSession session = new MiningSession(match.rule().id(), match.rule().location(), tool.orElse(null), entityId, drops, timeTicks, 0, -1, null, null);
        miningSessions.put(player.getUniqueId(), session);
        miningVisuals.show(player, match.rule().location(), match.variant().readyBlock(), match.variant().mining().feedback(), match.variant().mining().blockDisplayOverlay());
        sendCurrentVisualBlock(player, match);
        resumeCustomMining(player, match, session);
    }

    private void resumeCustomMining(Player player, BlockMatch match, MiningSession session) {
        if (session.timeoutTask() != null) {
            session.timeoutTask().cancel();
        }
        if (session.tool() != null && !toolMatches(player.getInventory().getItemInMainHand(), session.tool())) {
            cancelMining(player, true);
            sendCurrentVisualBlock(player, match);
            return;
        }
        CompatTask task = scheduler.regionTimer(match.rule().location(), 1L, 1L, ignored -> tickMining(player, match));
        MiningSession stored = miningSessions.get(player.getUniqueId());
        if (stored != null && stored.ruleId().equals(match.rule().id())) {
            miningSessions.put(player.getUniqueId(), stored.withTask(task, null));
        } else {
            task.cancel();
        }
    }

    private void pauseCustomMining(Player player, BlockMatch match) {
        MiningSession session = miningSessions.get(player.getUniqueId());
        if (session == null || !session.ruleId().equals(match.rule().id())) {
            sendCurrentVisualBlock(player, match);
            return;
        }
        if (session.task() != null) {
            session.task().cancel();
        }
        miningVisuals.clear(player);
        clearBreakAnimation(player, match.rule());
        sendCurrentVisualBlock(player, match);

        CompatTask timeout = scheduler.entityLater(player, 12L, task -> {
            MiningSession stored = miningSessions.get(player.getUniqueId());
            if (stored != null && stored.ruleId().equals(match.rule().id()) && stored.task() == null) {
                cancelMining(player, true);
                if (player.isOnline() && isVisible(player, match.rule())) {
                    sendCurrentVisualBlock(player, match);
                }
            }
        });
        miningSessions.put(player.getUniqueId(), session.withTask(null, timeout));
    }

    private void tickMining(Player player, BlockMatch match) {
        MiningSession session = miningSessions.get(player.getUniqueId());
        if (session == null || !session.ruleId().equals(match.rule().id())) {
            return;
        }
        if (!player.isOnline()
                || player.getWorld() != match.rule().location().getWorld()
                || player.getLocation().distanceSquared(match.rule().location()) > 36.0D
                || !isVisible(player, match.rule())
                || (session.tool() != null && !toolMatches(player.getInventory().getItemInMainHand(), session.tool()))) {
            cancelMining(player, true);
            if (player.isOnline() && isVisible(player, match.rule())) {
                sendCurrentVisualBlock(player, match);
            }
            return;
        }

        int elapsed = session.elapsedTicks() + 1;
        sendCurrentVisualBlock(player, match);
        int progress = Math.min(100, (int) Math.floor(elapsed * 100.0D / session.timeTicks()));
        miningVisuals.updateProgress(player, progress, match.variant().mining().feedback());
        int stage = Math.min(9, (int) Math.floor((elapsed * 10.0D) / session.timeTicks()));
        if (stage != session.stage()) {
            packets.sendBlockBreakAnimation(player, match.rule().location(), session.animationEntityId(), stage);
        }
        sendMiningFeedback(player, match, session, elapsed, stage);

        if (elapsed >= session.timeTicks()) {
            miningSessions.remove(player.getUniqueId());
            if (session.task() != null) {
                session.task().cancel();
            }
            if (session.timeoutTask() != null) {
                session.timeoutTask().cancel();
            }
            clearBreakAnimation(player, match.rule());
            miningVisuals.clear(player);
            handleBlockBreak(player, match, session.drops());
            resendCurrentVisualBlock(player, match, 2L);
            resendCurrentVisualBlock(player, match, 5L);
            return;
        }

        miningSessions.put(player.getUniqueId(), session.withProgress(elapsed, stage));
    }

    private void sendMiningFeedback(Player player, BlockMatch match, MiningSession session, int elapsed, int stage) {
        BlockMiningFeedback feedback = match.variant().mining().feedback();
        int progress = Math.min(100, (int) Math.floor(elapsed * 100.0D / session.timeTicks()));
        boolean intervalTick = elapsed == 1 || elapsed % feedback.intervalTicks() == 0;

        if (feedback.actionBar() && intervalTick) {
            player.sendActionBar(Text.mm(feedback.message().replace("{progress}", String.valueOf(progress))));
        }

        Location center = match.rule().location().toBlockLocation().add(0.5D, 0.5D, 0.5D);
        if (feedback.particles() && intervalTick) {
            player.spawnParticle(Particle.CRIT, center, 4, 0.28D, 0.28D, 0.28D, 0.01D);
            if (progress >= 75) {
                player.spawnParticle(Particle.DUST, center, 2, 0.22D, 0.22D, 0.22D, 0.0D, new Particle.DustOptions(Color.GRAY, 0.8F));
            }
        }

        if (feedback.sounds() && (elapsed == 1 || stage != session.stage())) {
            player.playSound(center, Sound.BLOCK_STONE_HIT, 0.35F, 0.75F + (stage * 0.035F));
        }
    }

    private Optional<BlockToolRule> matchingTool(Player player, BlockMiningConfig mining) {
        return matchingTool(player, mining.tools());
    }

    private Optional<BlockToolRule> matchingTool(Player player, List<BlockToolRule> tools) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return tools.stream()
                .filter(rule -> toolMatches(item, rule))
                .min(Comparator.comparingInt(this::toolMatchPriority));
    }

    private boolean toolMatches(ItemStack item, BlockToolRule rule) {
        if ("any".equalsIgnoreCase(rule.type())) {
            return true;
        }
        if ("mmoitems".equalsIgnoreCase(rule.type())) {
            return hooks.mmoItemMatches(item, rule.mmoType(), rule.mmoId());
        }
        return rule.material() != null && item != null && item.getType() == rule.material();
    }

    private int toolMatchPriority(BlockToolRule rule) {
        if ("mmoitems".equalsIgnoreCase(rule.type())) {
            return 0;
        }
        if ("any".equalsIgnoreCase(rule.type())) {
            return 2;
        }
        return 1;
    }

    private String dropSource(BlockToolRule tool, List<ConfigurationSection> blockDrops, boolean naturalFallback) {
        if (tool != null && !tool.drops().isEmpty()) {
            return "tool";
        }
        if (!blockDrops.isEmpty()) {
            return "block";
        }
        return naturalFallback ? "natural-break-fallback" : "block";
    }

    private void debugToolMatch(Player player, String feature, String target, List<BlockToolRule> tools, Optional<BlockToolRule> tool) {
        if (!debugEnabled()) {
            return;
        }
        if (tools.isEmpty()) {
            debug(player, feature + " " + target + " has no tool rules; any held item is allowed");
            return;
        }
        if (tool.isEmpty()) {
            debug(player, feature + " " + target + " matched no tool rule for held item " + heldItemName(player));
            return;
        }
        BlockToolRule matched = tool.get();
        debug(player, feature + " " + target + " matched tool rule " + describeTool(matched)
                + " via " + toolMatchSource(matched) + " for held item " + heldItemName(player));
    }

    private void debugDrops(Player player, String feature, String target, String source) {
        debug(player, feature + " " + target + " drops source: " + source);
    }

    private boolean debugEnabled() {
        return configManager.getMain().getBoolean("settings.debug", false);
    }

    private void debug(Player player, String message) {
        if (!debugEnabled()) {
            return;
        }
        plugin.getLogger().info("[debug] " + player.getName() + ": " + message);
    }

    private String describeTool(BlockToolRule rule) {
        if ("mmoitems".equalsIgnoreCase(rule.type())) {
            return "mmoitems:" + blank(rule.mmoType()) + "/" + blank(rule.mmoId());
        }
        if ("any".equalsIgnoreCase(rule.type())) {
            return "any";
        }
        return "vanilla:" + (rule.material() == null ? "unknown" : rule.material().name());
    }

    private String toolMatchSource(BlockToolRule rule) {
        if ("mmoitems".equalsIgnoreCase(rule.type())) {
            return "MMOItems";
        }
        if ("any".equalsIgnoreCase(rule.type())) {
            return "any";
        }
        return "vanilla";
    }

    private String heldItemName(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.isEmpty()) {
            return "AIR";
        }
        return item.getType().name();
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private void cancelMining(Player player, boolean clearAnimation) {
        MiningSession session = miningSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.task() != null) {
            session.task().cancel();
        }
        if (session.timeoutTask() != null) {
            session.timeoutTask().cancel();
        }
        miningVisuals.clear(player);
        if (clearAnimation) {
            packets.sendBlockBreakAnimation(player, session.location(), session.animationEntityId(), -1);
        }
    }

    private void cancelAllMining() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            cancelMining(player, true);
        }
        miningSessions.clear();
    }

    private void cancelAllFarmingGrowth() {
        for (Map<String, FarmingState> states : farmingStates.values()) {
            for (FarmingState state : states.values()) {
                state.tasks().forEach(CompatTask::cancel);
            }
        }
        farmingStates.clear();
    }

    private void clearBreakAnimation(Player player, BlockRule rule) {
        packets.sendBlockBreakAnimation(player, rule.location(), breakAnimationEntityId(player, rule), -1);
    }

    private int breakAnimationEntityId(Player player, BlockRule rule) {
        return Objects.hash(player.getUniqueId(), rule.id(), rule.location().getBlockX(), rule.location().getBlockY(), rule.location().getBlockZ());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        Optional<BlockMatch> match = ruleForLocation(player,
                event.getClickedBlock().getX(),
                event.getClickedBlock().getY(),
                event.getClickedBlock().getZ());
        if (match.isEmpty() || !isVisible(player, match.get().rule())) return;

        event.setCancelled(true);
        if (match.get().variant().farming().enabled()) {
            handleFarming(player, match.get());
            return;
        }
        sendCurrentVisualBlock(player, match.get());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Optional<VanillaBlockMiningRule> rule = vanillaRuleFor(player, block);
        if (rule.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        startVanillaMining(player, block, rule.get());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Optional<VanillaBlockMiningRule> rule = vanillaRuleFor(player, block);
        if (rule.isEmpty()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduler.regionLater(player.getLocation(), joinDelayTicks, task -> refreshAround(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelMining(event.getPlayer(), false);
        cancelVanillaMining(event.getPlayer(), false);
        regenerating.remove(event.getPlayer().getUniqueId());
        Map<String, FarmingState> states = farmingStates.remove(event.getPlayer().getUniqueId());
        if (states != null) {
            states.values().forEach(state -> state.tasks().forEach(CompatTask::cancel));
        }
        visibleBlocks.remove(event.getPlayer().getUniqueId());
        support.clear(event.getPlayer());
        miningVisuals.clear(event.getPlayer());
    }

    public int ruleCount() {
        return rules.size();
    }

    public int vanillaMiningRuleCount() {
        return vanillaMiningRules.size();
    }

    public int farmingRuleCount() {
        return (int) rules.stream()
                .filter(rule -> rule.variants().stream().anyMatch(variant -> variant.farming().enabled()))
                .count();
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
                MiningSession session = miningSessions.get(player.getUniqueId());
                boolean isMining = session != null && session.ruleId().equals(rule.id());
                FarmingState farmingState = farmingState(player, rule.id());
                if (farmingState != null && variant.farming().enabled() && !variant.farming().stages().isEmpty()) {
                    sendBlockData(player, loc, farmingStageBlockData(new BlockMatch(rule, variant), farmingState.stageIndex()));
                    if (!isVisible) visible.put(rule.id(), loc);
                    continue;
                }
                String blockMaterial = isRegenerating
                        ? variant.cooldownBlock()
                        : isMining
                        ? (variant.mining().activeBlock() == null || variant.mining().activeBlock().isBlank() ? "BARRIER" : variant.mining().activeBlock())
                        : variant.readyBlock();
                sendConfiguredBlock(player, loc, blockMaterial);
                if (!isVisible) visible.put(rule.id(), loc);

            } else if (!shouldBeVisible && isVisible) {
                visible.remove(rule.id());
                support.removeBlock(player, loc);
                sendRealBlock(player, loc);
            }
        }
    }

    private void restoreVisible(Player player) {
        Map<String, Location> visible = visibleBlocks.remove(player.getUniqueId());
        if (visible == null || visible.isEmpty()) return;
        for (Location loc : visible.values()) {
            World world = loc.getWorld();
            if (world == null || !loc.isChunkLoaded()) continue;
            support.removeBlock(player, loc);
            sendRealBlock(player, loc);
        }
    }

    private void sendConfiguredBlock(Player player, Location location, String blockMaterial) {
        if ("ORIGINAL".equalsIgnoreCase(blockMaterial)) {
            sendRealBlock(player, location);
            return;
        }
        sendBlockData(player, location, configuredBlockData(blockMaterial));
    }

    private BlockData configuredBlockData(String blockMaterial) {
        Optional<BlockData> customData = hooks.customBlockData(blockMaterial);
        if (customData.isPresent()) {
            return customData.get();
        }
        if (blockMaterial != null && blockMaterial.contains("[")) {
            try {
                return Bukkit.createBlockData(normalizeBlockDataString(blockMaterial));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Material mat = Material.matchMaterial(blockMaterial);
        return mat != null ? Bukkit.createBlockData(mat) : Bukkit.createBlockData(Material.AIR);
    }

    private String normalizeBlockDataString(String input) {
        String trimmed = input.trim();
        int properties = trimmed.indexOf('[');
        if (properties <= 0) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        String material = trimmed.substring(0, properties).toLowerCase(Locale.ROOT);
        if (!material.contains(":")) {
            material = "minecraft:" + material;
        }
        return material + trimmed.substring(properties).toLowerCase(Locale.ROOT);
    }

    private void sendBlockData(Player player, Location location, BlockData data) {
        packets.sendBlock(player, location, data);
        support.sendBlock(player, location, data);
    }

    private void resendCurrentVisualBlock(Player player, BlockMatch match, long delayTicks) {
        scheduler.entityLater(player, delayTicks, task -> {
            if (player.isOnline() && isVisible(player, match.rule())) {
                sendCurrentVisualBlock(player, match);
            }
        });
    }

    private void sendRealBlock(Player player, Location location) {
        scheduler.region(location, () -> {
            World world = location.getWorld();
            if (world == null || !location.isChunkLoaded()) return;
            BlockData data = world.getBlockAt(location).getBlockData();
            packets.sendBlock(player, location, data);
            support.sendBlock(player, location, data);
        });
    }

    private boolean isVisible(Player player, BlockRule rule) {
        Map<String, Location> visible = visibleBlocks.get(player.getUniqueId());
        return visible != null && visible.containsKey(rule.id());
    }

    private boolean isVisibleCoordinate(UUID playerId, int x, int y, int z) {
        Map<String, Location> visible = visibleBlocks.get(playerId);
        if (visible == null || visible.isEmpty()) return false;
        for (Location location : visible.values()) {
            if (location.getBlockX() == x && location.getBlockY() == y && location.getBlockZ() == z) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCustomMiningAt(Player player, int x, int y, int z) {
        for (BlockRule rule : rules) {
            Location loc = rule.location();
            if (loc.getBlockX() == x
                    && loc.getBlockY() == y
                    && loc.getBlockZ() == z
                    && loc.getWorld() == player.getWorld()
                    && rule.variants().stream().anyMatch(variant -> variant.mining().enabled())) {
                return true;
            }
        }
        return false;
    }

    private void resendVisibleBlock(Player player, int x, int y, int z) {
        Optional<BlockMatch> match = ruleForLocation(player, x, y, z);
        match.ifPresent(thisMatch -> {
            if (isVisible(player, thisMatch.rule())) {
                sendCurrentVisualBlock(player, thisMatch);
            }
        });
    }

    private void sendCurrentVisualBlock(Player player, BlockMatch match) {
        sendConfiguredBlock(player, match.rule().location(), currentVisualBlock(player, match));
    }

    private void acknowledgeCurrentVisualDig(Player player, BlockMatch match, DiggingAction action, boolean successful) {
        String blockMaterial = currentVisualBlock(player, match);
        if ("ORIGINAL".equalsIgnoreCase(blockMaterial)) {
            scheduler.region(match.rule().location(), () -> {
                World world = match.rule().location().getWorld();
                if (world != null && match.rule().location().isChunkLoaded()) {
                    packets.acknowledgeDig(player, match.rule().location(), world.getBlockAt(match.rule().location()).getBlockData(), action, successful);
                }
            });
            return;
        }
        packets.acknowledgeDig(player, match.rule().location(), configuredBlockData(blockMaterial), action, successful);
    }

    private String currentVisualBlock(Player player, BlockMatch match) {
        Set<String> activeRegen = regenerating.getOrDefault(player.getUniqueId(), Set.of());
        if (activeRegen.contains(match.rule().id())) {
            return match.variant().cooldownBlock();
        }
        MiningSession session = miningSessions.get(player.getUniqueId());
        if (session != null && session.ruleId().equals(match.rule().id())) {
            String activeBlock = match.variant().mining().activeBlock();
            return activeBlock == null || activeBlock.isBlank() ? "BARRIER" : activeBlock;
        }
        FarmingState farmingState = farmingState(player, match.rule().id());
        if (farmingState != null && match.variant().farming().enabled() && !match.variant().farming().stages().isEmpty()) {
            return match.variant().farming().stages().get(Math.max(0, Math.min(farmingState.stageIndex(), match.variant().farming().stages().size() - 1))).block();
        }
        return match.variant().readyBlock();
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
        if (items.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(ItemStack[]::new));
        if (!leftovers.isEmpty()) {
            Location dropLocation = player.getLocation();
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(dropLocation, leftover);
            }
        }
    }

    private record BlockMatch(BlockRule rule, BlockVariant variant) {
    }

    private record MiningSession(
            String ruleId,
            Location location,
            BlockToolRule tool,
            int animationEntityId,
            List<ConfigurationSection> drops,
            int timeTicks,
            int elapsedTicks,
            int stage,
            CompatTask task,
            CompatTask timeoutTask
    ) {
        MiningSession withTask(CompatTask task, CompatTask timeoutTask) {
            return new MiningSession(ruleId, location, tool, animationEntityId, drops, timeTicks, elapsedTicks, stage, task, timeoutTask);
        }

        MiningSession withProgress(int elapsedTicks, int stage) {
            return new MiningSession(ruleId, location, tool, animationEntityId, drops, timeTicks, elapsedTicks, stage, task, timeoutTask);
        }
    }

    private record VanillaMiningSession(
            Location location,
            VanillaBlockMiningRule rule,
            BlockToolRule tool,
            int animationEntityId,
            List<ConfigurationSection> drops,
            int timeTicks,
            int elapsedTicks,
            int stage,
            CompatTask task
    ) {
        VanillaMiningSession withTask(CompatTask task) {
            return new VanillaMiningSession(location, rule, tool, animationEntityId, drops, timeTicks, elapsedTicks, stage, task);
        }

        VanillaMiningSession withProgress(int elapsedTicks, int stage) {
            return new VanillaMiningSession(location, rule, tool, animationEntityId, drops, timeTicks, elapsedTicks, stage, task);
        }
    }

    private record FarmingState(int stageIndex, List<CompatTask> tasks) {
        FarmingState withStage(int stageIndex) {
            return new FarmingState(stageIndex, tasks);
        }
    }
}

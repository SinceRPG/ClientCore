package net.danh.clientcore.build;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import net.danh.clientcore.block.ClientBlockSupportService;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.util.CompatTask;
import net.danh.clientcore.util.FoliaScheduler;
import net.danh.clientcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientBuildService extends PacketListenerAbstract implements Listener {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final FoliaScheduler scheduler;
    private final ClientPacketService packets;
    private final ClientBlockSupportService support;
    private final ConditionEvaluator conditions;
    private final Map<UUID, BuildSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ClientBuildRule> builds = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> appliedBuilds = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> automaticBuilds = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> visibleBuilds = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Map<BlockKey, BlockData>>> appliedOriginals = new ConcurrentHashMap<>();
    private final Map<UUID, String> knownNames = new ConcurrentHashMap<>();
    private File generatedFolder;
    private File playersFile;
    private CompatTask refreshTask;
    private boolean enabled;
    private long refreshPeriodTicks;
    private long joinDelayTicks;

    public ClientBuildService(Plugin plugin, ConfigManager configManager, FoliaScheduler scheduler, ClientPacketService packets, ConditionEvaluator conditions, ClientBlockSupportService support) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
        this.packets = packets;
        this.support = support;
        this.conditions = conditions;
        this.generatedFolder = new File(plugin.getDataFolder(), "builds/generated");
        this.playersFile = new File(plugin.getDataFolder(), "client-build-players.yml");
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!enabled || event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;

        WrapperPlayClientPlayerBlockPlacement placement = new WrapperPlayClientPlayerBlockPlacement(event);
        Player player = (Player) event.getPlayer();
        if (player == null || !player.isOnline()) return;

        int x = placement.getBlockPosition().getX();
        int y = placement.getBlockPosition().getY();
        int z = placement.getBlockPosition().getZ();
        if (!hasVisibleCoordinate(player.getUniqueId(), x, y, z)) return;

        event.setCancelled(true);
        scheduler.entity(player, () -> resendVisibleBlock(player, x, y, z));
    }

    public void reload() {
        YamlConfiguration config = configManager.getBuilds();
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        restoreAllVisible();
        this.enabled = config.getBoolean("client-builds.enabled", true);
        this.refreshPeriodTicks = Math.max(20L, config.getLong("client-builds.refresh-period-ticks", 40L));
        this.joinDelayTicks = Math.max(1L, config.getLong("client-builds.join-delay-ticks", 10L));
        this.generatedFolder = dataFolder(config.getString("client-builds.generated-folder", "builds/generated"));
        this.playersFile = dataFile(config.getString("client-builds.players-file", "client-build-players.yml"), "client-build-players.yml");
        loadBuilds(config);
        loadPlayerApplications();
        refreshTask = scheduler.globalTimer(20L, refreshPeriodTicks, task -> {
            if (!enabled) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.entity(player, () -> refresh(player));
            }
        });
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.entity(player, () -> restoreApplications(player));
        }
    }

    public void shutdown() {
        savePlayerApplications();
        sessions.clear();
        appliedBuilds.clear();
        automaticBuilds.clear();
        visibleBuilds.clear();
        appliedOriginals.clear();
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public boolean startSession(Player player) {
        if (!enabled) return false;
        sessions.put(player.getUniqueId(), new BuildSession());
        return true;
    }

    public boolean stopSession(Player player) {
        return sessions.remove(player.getUniqueId()) != null;
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean saveSession(Player player, String buildName) {
        BuildSession session = sessions.get(player.getUniqueId());
        if (session == null || session.blocks().isEmpty()) {
            return false;
        }

        Map<BlockKey, BlockData> snapshot = Map.copyOf(session.blocks());
        YamlConfiguration config = configManager.getBuilds();
        ClientBuildRule rule = new ClientBuildRule(
                buildName,
                true,
                false,
                "",
                List.of(),
                snapshot
        );
        builds.put(buildName, rule);
        saveGeneratedBuild(rule);

        for (Map.Entry<BlockKey, BlockData> entry : snapshot.entrySet()) {
            BlockKey key = entry.getKey();
            BlockData original = session.originalBlocks().getOrDefault(key, Bukkit.createBlockData("minecraft:air"));
            Location location = key.toLocation();
            if (location == null) continue;
            scheduler.region(location, () -> {
                Block block = location.getBlock();
                block.setBlockData(original, false);
                sendRealBlockToAll(location);
            });
        }

        sessions.remove(player.getUniqueId());
        return true;
    }

    public boolean applyBuild(CommandSender sender, Player target, String buildName) {
        ClientBuildRule build = builds.get(buildName);
        if (build == null || build.blocks().isEmpty()) {
            Text.sendConfig(sender, configManager, "commands.client-build-not-found", "{build}", buildName);
            return false;
        }

        UUID targetId = target.getUniqueId();
        knownNames.put(targetId, target.getName());
        appliedBuilds.computeIfAbsent(targetId, ignored -> ConcurrentHashMap.newKeySet()).add(buildName);
        appliedOriginals.computeIfAbsent(targetId, ignored -> new ConcurrentHashMap<>()).computeIfAbsent(buildName, ignored -> new ConcurrentHashMap<>());
        savePlayerApplications();
        restoreApplications(target);
        return true;
    }

    public boolean removeBuild(Player target, String buildName) {
        UUID targetId = target.getUniqueId();
        Set<String> targetBuilds = appliedBuilds.get(targetId);
        if (targetBuilds == null || !targetBuilds.remove(buildName)) {
            return false;
        }

        restoreOriginals(target, buildName);
        Set<String> targetVisible = visibleBuilds.get(targetId);
        if (targetVisible != null) {
            targetVisible.remove(buildName);
            if (targetVisible.isEmpty()) visibleBuilds.remove(targetId);
        }
        Map<String, Map<BlockKey, BlockData>> mutableOriginals = appliedOriginals.get(targetId);
        if (mutableOriginals != null) {
            mutableOriginals.remove(buildName);
            if (mutableOriginals.isEmpty()) appliedOriginals.remove(targetId);
        }
        if (targetBuilds.isEmpty()) appliedBuilds.remove(targetId);
        savePlayerApplications();
        return true;
    }

    public Set<String> buildNames() {
        return Set.copyOf(builds.keySet());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        Location location = event.getClickedBlock().getLocation();
        BlockKey key = BlockKey.from(location);
        BlockData visualData = visibleBlockData(player, key);
        if (visualData == null) return;

        event.setCancelled(true);
        packets.sendBlock(player, location, visualData);
        support.sendBlock(player, location, visualData);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        BuildSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;

        Location location = event.getBlockPlaced().getLocation();
        BlockKey key = BlockKey.from(location);
        session.originalBlocks().putIfAbsent(key, event.getBlockReplacedState().getBlockData());
        session.blocks().put(key, event.getBlockPlaced().getBlockData());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        BuildSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;

        Location location = event.getBlock().getLocation();
        BlockKey key = BlockKey.from(location);
        if (session.blocks().remove(key) != null) {
            return;
        }

        session.originalBlocks().putIfAbsent(key, event.getBlock().getBlockData());
        session.blocks().put(key, Bukkit.createBlockData("minecraft:air"));
        event.setCancelled(true);
        packets.sendBlock(event.getPlayer(), location, Bukkit.createBlockData("minecraft:air"));
        support.removeBlock(event.getPlayer(), location);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        knownNames.put(player.getUniqueId(), player.getName());
        scheduler.regionLater(player.getLocation(), joinDelayTicks, task -> restoreApplications(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        knownNames.put(playerId, event.getPlayer().getName());
        sessions.remove(playerId);
        visibleBuilds.remove(playerId);
        automaticBuilds.remove(playerId);
        appliedOriginals.remove(playerId);
        savePlayerApplications();
    }

    private void refresh(Player player) {
        Set<String> targetBuilds = appliedBuilds.get(player.getUniqueId());
        Set<String> visibleCandidates = visibleCandidates(player, targetBuilds);
        if (visibleCandidates.isEmpty()) return;
        for (String buildName : visibleCandidates) {
            ClientBuildRule build = builds.get(buildName);
            if (build == null) continue;
            if (canSee(player, build)) {
                sendBuild(player, build);
            } else {
                restoreOriginals(player, buildName);
                Set<String> targetVisible = visibleBuilds.get(player.getUniqueId());
                if (targetVisible != null) {
                    targetVisible.remove(buildName);
                    if (targetVisible.isEmpty()) visibleBuilds.remove(player.getUniqueId());
                }
            }
        }
    }

    private void restoreApplications(Player player) {
        Set<String> targetBuilds = appliedBuilds.get(player.getUniqueId());
        Set<String> visibleCandidates = visibleCandidates(player, targetBuilds);
        if (visibleCandidates.isEmpty()) return;
        for (String buildName : visibleCandidates) {
            ClientBuildRule build = builds.get(buildName);
            if (build != null && canSee(player, build)) {
                sendBuild(player, build);
            }
        }
    }

    private void sendBuild(Player player, ClientBuildRule build) {
        UUID playerId = player.getUniqueId();
        Map<String, Map<BlockKey, BlockData>> originals = appliedOriginals.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        Map<BlockKey, BlockData> originalState = originals.computeIfAbsent(build.id(), ignored -> new ConcurrentHashMap<>());
        visibleBuilds.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(build.id());
        for (Map.Entry<BlockKey, BlockData> entry : build.blocks().entrySet()) {
            Location location = entry.getKey().toLocation();
            if (location == null || location.getWorld() != player.getWorld() || !location.isChunkLoaded()) continue;
            scheduler.region(location, () -> {
                originalState.putIfAbsent(entry.getKey(), location.getBlock().getBlockData());
                packets.sendBlock(player, location, entry.getValue());
                support.sendBlock(player, location, entry.getValue());
            });
        }
    }

    private void restoreOriginals(Player player, String buildName) {
        Map<BlockKey, BlockData> originals = appliedOriginals.getOrDefault(player.getUniqueId(), Map.of()).get(buildName);
        if (originals == null || originals.isEmpty()) {
            ClientBuildRule build = builds.get(buildName);
            if (build == null) return;
            for (BlockKey key : build.blocks().keySet()) {
                Location location = key.toLocation();
                if (location != null && location.getWorld() == player.getWorld()) {
                    scheduler.region(location, () -> packets.sendBlock(player, location, location.getBlock().getBlockData()));
                    support.removeBlock(player, location);
                }
            }
            return;
        }
        for (Map.Entry<BlockKey, BlockData> entry : originals.entrySet()) {
            Location location = entry.getKey().toLocation();
            if (location != null && location.getWorld() == player.getWorld()) {
                packets.sendBlock(player, location, entry.getValue());
                support.removeBlock(player, location);
            }
        }
    }

    private void restoreAllVisible() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<String> visible = visibleBuilds.get(player.getUniqueId());
            if (visible == null || visible.isEmpty()) continue;
            for (String buildName : Set.copyOf(visible)) {
                restoreOriginals(player, buildName);
            }
        }
        visibleBuilds.clear();
        automaticBuilds.clear();
        appliedOriginals.clear();
    }

    private boolean canSee(Player player, ClientBuildRule build) {
        return conditions.evaluate(player, build.condition(), build.conditions()).passed();
    }

    private BlockData visibleBlockData(Player player, BlockKey key) {
        Set<String> visible = visibleBuilds.get(player.getUniqueId());
        if (visible == null || visible.isEmpty()) return null;
        for (String buildName : visible) {
            ClientBuildRule build = builds.get(buildName);
            if (build == null) continue;
            BlockData data = build.blocks().get(key);
            if (data != null) return data;
        }
        return null;
    }

    private boolean hasVisibleCoordinate(UUID playerId, int x, int y, int z) {
        Set<String> visible = visibleBuilds.get(playerId);
        if (visible == null || visible.isEmpty()) return false;
        for (String buildName : visible) {
            ClientBuildRule build = builds.get(buildName);
            if (build == null) continue;
            for (BlockKey key : build.blocks().keySet()) {
                if (key.x() == x && key.y() == y && key.z() == z) {
                    return true;
                }
            }
        }
        return false;
    }

    private void resendVisibleBlock(Player player, int x, int y, int z) {
        World world = player.getWorld();
        BlockKey key = new BlockKey(world.getName(), x, y, z);
        BlockData visualData = visibleBlockData(player, key);
        if (visualData == null) return;

        Location location = new Location(world, x, y, z);
        packets.sendBlock(player, location, visualData);
        support.sendBlock(player, location, visualData);
    }

    private void sendRealBlockToAll(Location location) {
        BlockData realData = location.getBlock().getBlockData();
        for (Player online : Bukkit.getOnlinePlayers()) {
            packets.sendBlock(online, location, realData);
            support.removeBlock(online, location);
        }
    }

    private void loadBuilds(YamlConfiguration config) {
        builds.clear();
        ConfigurationSection root = config.getConfigurationSection("client-builds.rules");
        if (root == null) return;
        for (String buildName : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(buildName);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            Map<BlockKey, BlockData> blocks = new ConcurrentHashMap<>();
            ConfigurationSection blockSection = section.getConfigurationSection("blocks");
            if (blockSection == null) continue;
            for (String keyText : blockSection.getKeys(false)) {
                BlockKey key = BlockKey.parse(keyText);
                String blockData = blockSection.getString(keyText);
                if (key == null || blockData == null || blockData.isBlank()) continue;
                blocks.put(key, Bukkit.createBlockData(blockData));
            }
            if (!blocks.isEmpty()) {
                builds.put(buildName, new ClientBuildRule(
                        buildName,
                        section.getBoolean("enabled", true),
                        section.getBoolean("auto-apply", false),
                        section.getString("condition", ""),
                        section.getStringList("conditions"),
                        blocks
                ));
            }
        }
    }

    private void saveGeneratedBuild(ClientBuildRule build) {
        if (!generatedFolder.exists() && !generatedFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create " + generatedFolder.getPath());
            return;
        }
        File file = new File(generatedFolder, safeFileName(build.id()) + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        String root = "client-builds.rules." + build.id();
        config.set(root + ".enabled", build.enabled());
        config.set(root + ".auto-apply", build.autoApply());
        config.set(root + ".condition", build.condition());
        config.set(root + ".conditions", build.conditions());
        ConfigurationSection blockSection = config.createSection(root + ".blocks");
        for (Map.Entry<BlockKey, BlockData> entry : build.blocks().entrySet()) {
            blockSection.set(entry.getKey().serialize(), entry.getValue().getAsString());
        }
        save(config, file);
    }

    private void loadPlayerApplications() {
        appliedBuilds.clear();
        visibleBuilds.clear();
        automaticBuilds.clear();
        appliedOriginals.clear();
        ensureParent(playersFile);
        if (!playersFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidText : players.getKeys(false)) {
            UUID uuid = parseUuid(uuidText);
            if (uuid == null) continue;
            knownNames.put(uuid, players.getString(uuidText + ".name", uuidText));
            Set<String> playerBuilds = ConcurrentHashMap.newKeySet();
            for (String buildName : players.getStringList(uuidText + ".applied")) {
                if (builds.containsKey(buildName)) {
                    playerBuilds.add(buildName);
                }
            }
            if (!playerBuilds.isEmpty()) {
                appliedBuilds.put(uuid, playerBuilds);
            }
        }
    }

    private void savePlayerApplications() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection players = config.createSection("players");
        for (Map.Entry<UUID, Set<String>> entry : appliedBuilds.entrySet()) {
            String uuid = entry.getKey().toString();
            players.set(uuid + ".name", knownNames.getOrDefault(entry.getKey(), uuid));
            players.set(uuid + ".applied", new ArrayList<>(entry.getValue()));
        }
        save(config, playersFile);
    }

    private File dataFolder(String configuredName) {
        String safeName = configuredName == null || configuredName.isBlank() ? "builds/generated" : configuredName;
        File file = new File(safeName);
        return file.isAbsolute() ? file : new File(plugin.getDataFolder(), safeName);
    }

    private File dataFile(String configuredName, String fallback) {
        String safeName = configuredName == null || configuredName.isBlank() ? fallback : configuredName;
        File file = new File(safeName);
        return file.isAbsolute() ? file : new File(plugin.getDataFolder(), safeName);
    }

    private void ensureParent(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Failed to create " + parent.getPath());
        }
    }

    private void save(YamlConfiguration config, File file) {
        ensureParent(file);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save " + file.getName());
        }
    }

    private UUID parseUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String safeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Set<String> visibleCandidates(Player player, Set<String> assignedBuilds) {
        Set<String> result = ConcurrentHashMap.newKeySet();
        if (assignedBuilds != null) {
            result.addAll(assignedBuilds);
        }
        result.addAll(visibleBuilds.getOrDefault(player.getUniqueId(), Set.of()));
        Set<String> auto = automaticBuilds.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        auto.clear();
        for (ClientBuildRule build : builds.values()) {
            if (build.enabled() && build.autoApply() && canSee(player, build)) {
                auto.add(build.id());
                result.add(build.id());
            }
        }
        return result;
    }

    private record ClientBuildRule(String id, boolean enabled, boolean autoApply, String condition,
                                   List<String> conditions, Map<BlockKey, BlockData> blocks) {
    }

    private record BuildSession(Map<BlockKey, BlockData> blocks, Map<BlockKey, BlockData> originalBlocks) {
        private BuildSession() {
            this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        }
    }

    private record BlockKey(String world, int x, int y, int z) {
        private static BlockKey from(Location location) {
            World world = location.getWorld();
            return new BlockKey(world == null ? "" : world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private static BlockKey parse(String input) {
            String[] parts = input.split(",", 4);
            if (parts.length != 4) return null;
            try {
                return new BlockKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private String serialize() {
            return world + "," + x + "," + y + "," + z;
        }

        private Location toLocation() {
            World bukkitWorld = Bukkit.getWorld(world);
            return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
        }
    }
}

package net.danh.clientcore;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.util.TimeStampMode;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import net.danh.clientcore.block.BlockRegenService;
import net.danh.clientcore.block.ClientBlockSupportService;
import net.danh.clientcore.build.ClientBuildService;
import net.danh.clientcore.chest.ClientLootChestService;
import net.danh.clientcore.command.ClientCoreCommands;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.drop.ClientDropService;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.hook.WorldGuardFlagRegistrar;
import net.danh.clientcore.hook.plugin.ModelEngineClientMobListener;
import net.danh.clientcore.luck.LuckItemService;
import net.danh.clientcore.luck.LuckService;
import net.danh.clientcore.mob.ClientMobService;
import net.danh.clientcore.npc.ClientNpcService;
import net.danh.clientcore.packet.ClientMobPacketListener;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.storage.CooldownManager;
import net.danh.clientcore.storage.StorageService;
import net.danh.clientcore.util.FoliaScheduler;
import net.danh.clientcore.util.Text;
import net.danh.clientcore.visibility.VisibilityService;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClientCore extends JavaPlugin {
    private ConfigManager configManager;
    private FoliaScheduler scheduler;
    private HookRegistry hooks;
    private ClientPacketService packets;
    private StorageService storageService;
    private CooldownManager cooldownManager;
    private LuckService luckService;
    private LuckItemService luckItemService;
    private ClientBlockSupportService clientBlockSupportService;
    private BlockRegenService blockRegenService;
    private ClientMobService clientMobService;
    private ClientNpcService clientNpcService;
    private ClientDropService clientDropService;
    private ClientLootChestService clientLootChestService;
    private ClientBuildService clientBuildService;
    private VisibilityService visibilityService;
    private ConditionEvaluator conditionEvaluator;
    private boolean ownsPacketEvents;

    @Override
    public void onLoad() {
        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();
        initializeBundledPacketEvents();
        try {
            WorldGuardFlagRegistrar.register(this, configManager);
        } catch (NoClassDefFoundError ignored) {
            Text.log(this, configManager, "console.wg-not-found");
        }
    }

    @Override
    public void onEnable() {
        this.scheduler = new FoliaScheduler(this);
        this.hooks = new HookRegistry(this, configManager);
        enableBundledPacketEvents();
        this.packets = new ClientPacketService(this, scheduler);
        this.conditionEvaluator = new ConditionEvaluator(hooks);

        this.storageService = new StorageService(this, configManager);
        this.storageService.start();

        this.cooldownManager = new CooldownManager(storageService);
        this.luckService = new LuckService(storageService);
        this.luckItemService = new LuckItemService(this, configManager, luckService, hooks);

        this.clientBlockSupportService = new ClientBlockSupportService(this, configManager, scheduler);
        this.blockRegenService = new BlockRegenService(this, configManager, scheduler, hooks, packets, luckService, clientBlockSupportService);
        this.clientMobService = new ClientMobService(this, configManager, scheduler, hooks, packets, luckService, storageService);
        this.clientNpcService = new ClientNpcService(this, configManager, scheduler, hooks, packets, conditionEvaluator);
        this.clientDropService = new ClientDropService(this, configManager, scheduler, hooks, packets, conditionEvaluator, cooldownManager);
        this.clientLootChestService = new ClientLootChestService(this, configManager, scheduler, hooks, packets, conditionEvaluator, cooldownManager);
        this.clientBuildService = new ClientBuildService(this, configManager, scheduler, packets, conditionEvaluator, clientBlockSupportService);

        this.visibilityService = new VisibilityService(this, scheduler);

        clientBlockSupportService.start();
        blockRegenService.reload();
        clientMobService.reload();
        clientNpcService.reload();
        clientDropService.reload();
        clientLootChestService.reload();
        clientBuildService.reload();

        PacketEvents.getAPI().getEventManager().registerListener(new ClientMobPacketListener(clientMobService), PacketListenerPriority.NORMAL);

        getServer().getPluginManager().registerEvents(cooldownManager, this);
        getServer().getPluginManager().registerEvents(clientBlockSupportService, this);
        getServer().getPluginManager().registerEvents(blockRegenService, this);
        getServer().getPluginManager().registerEvents(clientMobService, this);
        if (hooks.hasModelEngine()) {
            try {
                getServer().getPluginManager().registerEvents(new ModelEngineClientMobListener(this, scheduler, clientMobService), this);
            } catch (NoClassDefFoundError ignored) {
                // ModelEngine was disabled or missing during class loading.
            }
        }
        getServer().getPluginManager().registerEvents(clientNpcService, this);
        getServer().getPluginManager().registerEvents(clientDropService, this);
        getServer().getPluginManager().registerEvents(clientLootChestService, this);
        getServer().getPluginManager().registerEvents(clientBuildService, this);
        getServer().getPluginManager().registerEvents(luckService, this);
        getServer().getPluginManager().registerEvents(luckItemService, this);
        getServer().getPluginManager().registerEvents(visibilityService, this);

        ClientCoreCommands.register(this, configManager, blockRegenService, clientMobService, clientBuildService, visibilityService, luckService, luckItemService);

        for (Player player : getServer().getOnlinePlayers()) {
            luckService.load(player);
        }

        Text.log(this, configManager, "console.enabled", "{version}", getServer().getMinecraftVersion());
    }

    @Override
    public void onDisable() {
        if (clientBlockSupportService != null) clientBlockSupportService.shutdown();
        if (blockRegenService != null) blockRegenService.shutdown();
        if (clientMobService != null) clientMobService.shutdown();
        if (clientNpcService != null) clientNpcService.shutdown();
        if (clientDropService != null) clientDropService.shutdown();
        if (clientLootChestService != null) clientLootChestService.shutdown();
        if (clientBuildService != null) clientBuildService.shutdown();
        if (storageService != null) storageService.close();

        HandlerList.unregisterAll(this);
        getServer().getGlobalRegionScheduler().cancelTasks(this);
        if (ownsPacketEvents && PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized()) {
            PacketEvents.getAPI().terminate();
        }
    }

    public void reloadPlugin() {
        configManager.loadAll();
        hooks.reload();
        clientBlockSupportService.reload();
        blockRegenService.reload();
        clientMobService.reload();
        clientNpcService.reload();
        clientDropService.reload();
        clientLootChestService.reload();
        clientBuildService.reload();
    }

    public FoliaScheduler scheduler() {
        return scheduler;
    }

    private void initializeBundledPacketEvents() {
        PacketEventsAPI<?> existing = PacketEvents.getAPI();
        if (existing != null) {
            ownsPacketEvents = false;
            return;
        }
        PacketEventsSettings settings = new PacketEventsSettings()
                .debug(false)
                .checkForUpdates(false)
                .timeStampMode(TimeStampMode.MILLIS)
                .reEncodeByDefault(true);
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this, settings));
        PacketEvents.getAPI().load();
        ownsPacketEvents = true;
    }

    private void enableBundledPacketEvents() {
        PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null) {
            initializeBundledPacketEvents();
            api = PacketEvents.getAPI();
        }
        if (api != null && !api.isInitialized()) {
            api.init();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public FoliaScheduler getScheduler() {
        return scheduler;
    }

    public HookRegistry getHooks() {
        return hooks;
    }

    public ClientPacketService getPackets() {
        return packets;
    }

    public StorageService getStorageService() {
        return storageService;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public LuckService getLuckService() {
        return luckService;
    }

    public LuckItemService getLuckItemService() {
        return luckItemService;
    }

    public ClientBlockSupportService getClientBlockSupportService() {
        return clientBlockSupportService;
    }

    public BlockRegenService getBlockRegenService() {
        return blockRegenService;
    }

    public ClientMobService getClientMobService() {
        return clientMobService;
    }

    public ClientNpcService getClientNpcService() {
        return clientNpcService;
    }

    public ClientDropService getClientDropService() {
        return clientDropService;
    }

    public ClientLootChestService getClientLootChestService() {
        return clientLootChestService;
    }

    public ClientBuildService getClientBuildService() {
        return clientBuildService;
    }

    public VisibilityService getVisibilityService() {
        return visibilityService;
    }

    public ConditionEvaluator getConditionEvaluator() {
        return conditionEvaluator;
    }

    public boolean isOwnsPacketEvents() {
        return ownsPacketEvents;
    }
}

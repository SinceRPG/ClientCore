package net.danh.clientcore;

import net.danh.clientcore.block.BlockRegenService;
import net.danh.clientcore.chest.ClientLootChestService;
import net.danh.clientcore.command.ClientCoreCommands;
import net.danh.clientcore.condition.ConditionEvaluator;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.drop.ClientDropService;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.hook.WorldGuardFlagRegistrar;
import net.danh.clientcore.luck.LuckItemService;
import net.danh.clientcore.luck.LuckService;
import net.danh.clientcore.mob.ClientMobService;
import net.danh.clientcore.npc.ClientNpcService;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.storage.CooldownManager;
import net.danh.clientcore.storage.StorageService;
import net.danh.clientcore.util.FoliaScheduler;
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
    private BlockRegenService blockRegenService;
    private ClientMobService clientMobService;
    private ClientNpcService clientNpcService;
    private ClientDropService clientDropService;
    private ClientLootChestService clientLootChestService;
    private VisibilityService visibilityService;
    private ConditionEvaluator conditionEvaluator;

    @Override
    public void onLoad() {
        try {
            WorldGuardFlagRegistrar.register(this);
        } catch (NoClassDefFoundError ignored) {
            getLogger().info("WorldGuard not found during load; custom flag registration skipped.");
        }
    }

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();

        this.scheduler = new FoliaScheduler(this);
        this.hooks = new HookRegistry(this, configManager);
        this.packets = new ClientPacketService();
        this.conditionEvaluator = new ConditionEvaluator(hooks);

        this.storageService = new StorageService(this, configManager);
        this.storageService.start();

        this.cooldownManager = new CooldownManager(storageService);
        this.luckService = new LuckService(storageService);
        this.luckItemService = new LuckItemService(this, configManager, luckService, hooks);

        this.blockRegenService = new BlockRegenService(this, configManager, scheduler, hooks, packets, luckService);
        this.clientMobService = new ClientMobService(this, configManager, scheduler, hooks, packets, luckService, storageService);
        this.clientNpcService = new ClientNpcService(this, configManager, scheduler, hooks, packets, conditionEvaluator);
        this.clientDropService = new ClientDropService(this, configManager, scheduler, hooks, packets, conditionEvaluator, cooldownManager);
        this.clientLootChestService = new ClientLootChestService(this, configManager, scheduler, hooks, packets, conditionEvaluator, cooldownManager);

        this.visibilityService = new VisibilityService(this);

        blockRegenService.reload();
        clientMobService.reload();
        clientNpcService.reload();
        clientDropService.reload();
        clientLootChestService.reload();

        getServer().getPluginManager().registerEvents(cooldownManager, this);
        getServer().getPluginManager().registerEvents(blockRegenService, this);
        getServer().getPluginManager().registerEvents(clientMobService, this);
        getServer().getPluginManager().registerEvents(clientNpcService, this);
        getServer().getPluginManager().registerEvents(clientDropService, this);
        getServer().getPluginManager().registerEvents(clientLootChestService, this);
        getServer().getPluginManager().registerEvents(luckService, this);
        getServer().getPluginManager().registerEvents(luckItemService, this);
        getServer().getPluginManager().registerEvents(visibilityService, this);

        ClientCoreCommands.register(this, configManager, blockRegenService, clientMobService, visibilityService, luckService, luckItemService);

        for (Player player : getServer().getOnlinePlayers()) {
            luckService.load(player);
        }

        getLogger().info("ClientCore enabled for Paper/Folia " + getServer().getMinecraftVersion());
    }

    @Override
    public void onDisable() {
        if (blockRegenService != null) blockRegenService.shutdown();
        if (clientMobService != null) clientMobService.shutdown();
        if (clientNpcService != null) clientNpcService.shutdown();
        if (clientDropService != null) clientDropService.shutdown();
        if (clientLootChestService != null) clientLootChestService.shutdown();
        if (storageService != null) storageService.close();

        HandlerList.unregisterAll(this);
        getServer().getGlobalRegionScheduler().cancelTasks(this);
    }

    public void reloadPlugin() {
        configManager.loadAll();
        hooks.reload();
        blockRegenService.reload();
        clientMobService.reload();
        clientNpcService.reload();
        clientDropService.reload();
        clientLootChestService.reload();
    }

    public FoliaScheduler scheduler() {
        return scheduler;
    }
}
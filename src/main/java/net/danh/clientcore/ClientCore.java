package net.danh.clientcore;

import net.danh.clientcore.block.BlockRegenService;
import net.danh.clientcore.command.ClientCoreCommands;
import net.danh.clientcore.gui.EditorMenu;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.hook.WorldGuardFlagRegistrar;
import net.danh.clientcore.luck.LuckService;
import net.danh.clientcore.luck.LuckItemService;
import net.danh.clientcore.mob.ClientMobService;
import net.danh.clientcore.packet.ClientPacketService;
import net.danh.clientcore.storage.StorageService;
import net.danh.clientcore.util.FoliaScheduler;
import net.danh.clientcore.visibility.VisibilityService;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClientCore extends JavaPlugin {
    private FoliaScheduler scheduler;
    private HookRegistry hooks;
    private ClientPacketService packets;
    private StorageService storageService;
    private LuckService luckService;
    private LuckItemService luckItemService;
    private BlockRegenService blockRegenService;
    private ClientMobService clientMobService;
    private EditorMenu editorMenu;
    private VisibilityService visibilityService;

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
        saveDefaultConfig();

        this.scheduler = new FoliaScheduler(this);
        this.hooks = new HookRegistry(this);
        this.packets = new ClientPacketService();
        this.storageService = new StorageService(this);
        this.storageService.start();
        this.luckService = new LuckService(storageService);
        this.luckItemService = new LuckItemService(this, luckService, hooks);
        this.blockRegenService = new BlockRegenService(this, scheduler, hooks, packets, luckService);
        this.clientMobService = new ClientMobService(this, scheduler, hooks, packets, luckService, storageService);
        this.visibilityService = new VisibilityService(this);
        this.editorMenu = new EditorMenu(this, blockRegenService, clientMobService, hooks);

        blockRegenService.reload();
        clientMobService.reload();

        getServer().getPluginManager().registerEvents(blockRegenService, this);
        getServer().getPluginManager().registerEvents(clientMobService, this);
        getServer().getPluginManager().registerEvents(luckService, this);
        getServer().getPluginManager().registerEvents(luckItemService, this);
        getServer().getPluginManager().registerEvents(editorMenu, this);
        getServer().getPluginManager().registerEvents(visibilityService, this);
        ClientCoreCommands.register(this, blockRegenService, clientMobService, editorMenu, visibilityService, luckService, luckItemService);
        for (Player player : getServer().getOnlinePlayers()) {
            luckService.load(player);
        }

        getLogger().info("ClientCore enabled for Paper/Folia " + getServer().getMinecraftVersion());
    }

    @Override
    public void onDisable() {
        if (blockRegenService != null) {
            blockRegenService.shutdown();
        }
        if (clientMobService != null) {
            clientMobService.shutdown();
        }
        if (storageService != null) {
            storageService.close();
        }
        HandlerList.unregisterAll(this);
        getServer().getGlobalRegionScheduler().cancelTasks(this);
    }

    public void reloadPlugin() {
        reloadConfig();
        hooks.reload();
        blockRegenService.reload();
        clientMobService.reload();
    }

    public FoliaScheduler scheduler() {
        return scheduler;
    }
}

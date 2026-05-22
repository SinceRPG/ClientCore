package net.danh.clientcore.hook.plugin;

import com.ticxo.modelengine.api.events.AddModelEvent;
import net.danh.clientcore.mob.ClientMobService;
import net.danh.clientcore.util.FoliaScheduler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class ModelEngineClientMobListener implements Listener {
    private final Plugin plugin;
    private final FoliaScheduler scheduler;
    private final ClientMobService mobService;

    public ModelEngineClientMobListener(Plugin plugin, FoliaScheduler scheduler, ClientMobService mobService) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.mobService = mobService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onAddModel(AddModelEvent event) {
        Object original = event.getTarget().getBase().getOriginal();
        if (!(original instanceof Entity entity)) return;

        Runnable claim = () -> {
            Player owner = mobService.ownerPlayer(entity).orElse(null);
            if (owner == null) return;
            mobService.claimPacketEntityIds(ModelEngineHook.renderEntityIds(event.getModel()), owner);
            mobService.claimPacketEntityIds(ModelEngineHook.renderEntityIds(entity), owner);
            mobService.claimNearbyVisualEntities(entity, owner);
        };

        scheduler.region(entity.getLocation(), claim);
        scheduler.regionLater(entity.getLocation(), 1L, task -> claim.run());
        scheduler.regionLater(entity.getLocation(), 5L, task -> claim.run());
    }
}

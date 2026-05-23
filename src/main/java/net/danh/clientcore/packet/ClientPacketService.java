package net.danh.clientcore.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgePlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import net.danh.clientcore.util.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public final class ClientPacketService {
    private final Plugin plugin;
    private final FoliaScheduler scheduler;

    public ClientPacketService(Plugin plugin, FoliaScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    private static String normalizeBlock(BlockData blockData) {
        String input = blockData.getAsString().toLowerCase(Locale.ROOT);
        return input.contains(":") ? input : "minecraft:" + input;
    }

    public void sendBlock(Player player, Location location, BlockData blockData) {
        WrappedBlockState state = WrappedBlockState.getByString(normalizeBlock(blockData));
        if (state == null) {
            return;
        }
        send(player, new WrapperPlayServerBlockChange(
                new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                state
        ));
    }

    public void destroyEntity(Player player, int entityId) {
        send(player, new WrapperPlayServerDestroyEntities(entityId));
    }

    public void sendBlockBreakAnimation(Player player, Location location, int entityId, int stage) {
        send(player, new WrapperPlayServerBlockBreakAnimation(
                entityId,
                new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                (byte) Math.max(-1, Math.min(9, stage))
        ));
    }

    public void acknowledgeDig(Player player, Location location, BlockData blockData, DiggingAction action, boolean successful) {
        WrappedBlockState state = WrappedBlockState.getByString(normalizeBlock(blockData));
        if (state == null) {
            return;
        }
        send(player, new WrapperPlayServerAcknowledgePlayerDigging(
                action,
                successful,
                new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                state.getGlobalId()
        ));
    }

    private void send(Player player, PacketWrapper<?> wrapper) {
        if (!player.isOnline()) {
            return;
        }
        sendNow(player, wrapper);
    }

    private void sendNow(Player player, PacketWrapper<?> wrapper) {
        if (!player.isOnline()) {
            return;
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapper);
    }
}

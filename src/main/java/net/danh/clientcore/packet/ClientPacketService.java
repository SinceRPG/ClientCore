package net.danh.clientcore.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class ClientPacketService {
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

    public void sendAir(Player player, Location location) {
        send(player, new WrapperPlayServerBlockChange(
                new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                WrappedBlockState.getByString("minecraft:air")
        ));
    }

    public void destroyEntity(Player player, int entityId) {
        send(player, new WrapperPlayServerDestroyEntities(entityId));
    }

    private void send(Player player, PacketWrapper<?> wrapper) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapper);
    }

    private static String normalizeBlock(BlockData blockData) {
        String input = blockData.getAsString().toLowerCase(Locale.ROOT);
        return input.contains(":") ? input : "minecraft:" + input;
    }
}

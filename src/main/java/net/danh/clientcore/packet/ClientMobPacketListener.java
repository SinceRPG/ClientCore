package net.danh.clientcore.packet;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.danh.clientcore.mob.ClientMobService;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public class ClientMobPacketListener extends PacketListenerAbstract implements PacketListener {
    private final ClientMobService mobService;

    public ClientMobPacketListener(ClientMobService mobService) {
        this.mobService = mobService;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Map<UUID, UUID> owners = mobService.getOwners();
        Map<Integer, UUID> packetOwners = mobService.getPacketEntityOwners();
        if (owners.isEmpty() && packetOwners.isEmpty()) return;

        Map<Integer, ClientMobService.PacketEntityView> entityViews = mobService.getPacketEntityViews();

        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity spawnEntity = new WrapperPlayServerSpawnEntity(event);
            UUID packetOwner = packetOwners.get(spawnEntity.getEntityId());
            if (packetOwner != null && !packetOwner.equals(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            if (spawnEntity.getEntityType() == EntityTypes.ARMOR_STAND || spawnEntity.getEntityType() == EntityTypes.TEXT_DISPLAY
                    || spawnEntity.getEntityType() == EntityTypes.ITEM_DISPLAY || spawnEntity.getEntityType() == EntityTypes.BLOCK_DISPLAY
                    || spawnEntity.getEntityType() == EntityTypes.INTERACTION || spawnEntity.getEntityType() == EntityTypes.SLIME
                    || spawnEntity.getEntityType() == EntityTypes.MAGMA_CUBE) {
                if (shouldHide(player, spawnEntity.getPosition().getX(), spawnEntity.getPosition().getY(), spawnEntity.getPosition().getZ(), entityViews)) {
                    event.setCancelled(true);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.PARTICLE) {
            WrapperPlayServerParticle particle = new WrapperPlayServerParticle(event);
            if (shouldHide(player, particle.getPosition().getX(), particle.getPosition().getY(), particle.getPosition().getZ(), entityViews)) {
                event.setCancelled(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT) {
            WrapperPlayServerSoundEffect sound = new WrapperPlayServerSoundEffect(event);
            if (shouldHide(player, sound.getEffectPosition().getX(), sound.getEffectPosition().getY(), sound.getEffectPosition().getZ(), entityViews)) {
                event.setCancelled(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
            WrapperPlayServerEntitySoundEffect sound = new WrapperPlayServerEntitySoundEffect(event);
            UUID packetOwner = packetOwners.get(sound.getEntityId());
            if (packetOwner != null && !packetOwner.equals(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            ClientMobService.PacketEntityView source = entityViews.get(sound.getEntityId());
            if (source != null && !source.owner().equals(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    private boolean shouldHide(Player receiver, double x, double y, double z, Map<Integer, ClientMobService.PacketEntityView> entities) {
        double hideRadiusSquared = mobService.effectHideRadiusSquared();
        for (ClientMobService.PacketEntityView view : entities.values()) {
            double dx = view.x() - x;
            double dy = view.y() - y;
            double dz = view.z() - z;
            if ((dx * dx + dy * dy + dz * dz) <= hideRadiusSquared) {
                if (!view.owner().equals(receiver.getUniqueId())) {
                    return true;
                }
            }
        }
        return false;
    }
}

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
import org.bukkit.Location;
import org.bukkit.entity.Entity;
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
        if (owners.isEmpty()) return;

        Map<UUID, Entity> entities = mobService.getClientEntities();

        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity spawnEntity = new WrapperPlayServerSpawnEntity(event);
            if (spawnEntity.getEntityType() == EntityTypes.ARMOR_STAND || spawnEntity.getEntityType() == EntityTypes.TEXT_DISPLAY) {
                if (shouldHide(player, spawnEntity.getPosition().getX(), spawnEntity.getPosition().getY(), spawnEntity.getPosition().getZ(), owners, entities)) {
                    event.setCancelled(true);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.PARTICLE) {
            WrapperPlayServerParticle particle = new WrapperPlayServerParticle(event);
            if (shouldHide(player, particle.getPosition().getX(), particle.getPosition().getY(), particle.getPosition().getZ(), owners, entities)) {
                event.setCancelled(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT) {
            WrapperPlayServerSoundEffect sound = new WrapperPlayServerSoundEffect(event);
            if (shouldHide(player, sound.getEffectPosition().getX(), sound.getEffectPosition().getY(), sound.getEffectPosition().getZ(), owners, entities)) {
                event.setCancelled(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
            WrapperPlayServerEntitySoundEffect sound = new WrapperPlayServerEntitySoundEffect(event);
            Entity source = getEntityById(sound.getEntityId(), entities);
            if (source != null && !owners.getOrDefault(source.getUniqueId(), player.getUniqueId()).equals(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    private Entity getEntityById(int id, Map<UUID, Entity> entities) {
        for (Entity entity : entities.values()) {
            if (entity.getEntityId() == id) return entity;
        }
        return null;
    }

    private boolean shouldHide(Player receiver, double x, double y, double z, Map<UUID, UUID> owners, Map<UUID, Entity> entities) {
        for (Map.Entry<UUID, Entity> entry : entities.entrySet()) {
            Entity mob = entry.getValue();
            if (mob == null || !mob.isValid()) continue;

            Location loc = mob.getLocation();
            if (loc.getWorld() != receiver.getWorld()) continue;

            double dx = loc.getX() - x;
            double dy = loc.getY() - y;
            double dz = loc.getZ() - z;
            // Radius of 1.5 blocks to catch holograms and particles around the mob
            if ((dx * dx + dy * dy + dz * dz) <= 2.25) {
                UUID owner = owners.get(mob.getUniqueId());
                if (owner != null && !owner.equals(receiver.getUniqueId())) {
                    return true;
                }
            }
        }
        return false;
    }
}

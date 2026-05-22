package net.danh.clientcore.hook.plugin;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MythicMobsHook {
    public static Optional<Entity> spawn(String mobId, Location location, double level) {
        Optional<MythicMob> mob = MythicBukkit.inst().getMobManager().getMythicMob(mobId);
        if (mob.isEmpty()) return Optional.empty();
        return Optional.ofNullable(BukkitAdapter.adapt(
                mob.get().spawn(BukkitAdapter.adapt(location), level).getEntity()
        ));
    }

    public static List<String> getMobIds() {
        return MythicBukkit.inst().getMobManager().getMobNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static UUID getParentUUID(Entity entity) {
        try {
            ActiveMob am = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);
            if (am != null && am.getParent() != null && am.getParent().isPresent()) {
                return am.getParent().get().getUniqueId();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static boolean isMythicMob(Entity entity) {
        try {
            return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId()).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void setOwnerTarget(Entity entity, Player owner) {
        try {
            ActiveMob am = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);
            if (am == null) return;
            var adaptedOwner = BukkitAdapter.adapt(owner);
            am.setOwnerUUID(owner.getUniqueId());
            am.resetTarget();
            am.voidTargetChange();
            am.setTarget(adaptedOwner);
            if (am.hasThreatTable()) {
                am.getThreatTable().dropCombat();
                am.getThreatTable().threatSet(adaptedOwner, Double.MAX_VALUE);
                am.getThreatTable().targetThreateningEntity(adaptedOwner);
            }
        } catch (Exception ignored) {
        }
    }
}

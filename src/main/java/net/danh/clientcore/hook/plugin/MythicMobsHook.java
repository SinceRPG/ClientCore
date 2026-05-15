package net.danh.clientcore.hook.plugin;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.Optional;

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
}
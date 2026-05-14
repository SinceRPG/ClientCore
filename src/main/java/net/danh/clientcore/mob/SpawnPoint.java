package net.danh.clientcore.mob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

public final class SpawnPoint {
    private final String id;
    private String world;
    private double x;
    private double y;
    private double z;
    private String rule;
    private int amount;
    private double radius;
    private int batchSize;
    private int intervalTicks;
    private int maxAlive;
    private double level;
    private double activationRange;
    private boolean enabled;

    public SpawnPoint(String id, Location location) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.rule = "";
        this.amount = 1;
        this.radius = 4.0D;
        this.batchSize = 1;
        this.intervalTicks = 100;
        this.maxAlive = 8;
        this.level = 1.0D;
        this.activationRange = 48.0D;
        this.enabled = true;
    }

    public SpawnPoint(String id, String world, double x, double y, double z, String rule, int amount, double radius,
                      int batchSize, int intervalTicks, int maxAlive, double level, double activationRange, boolean enabled) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rule = rule == null ? "" : rule;
        this.amount = Math.max(1, amount);
        this.radius = Math.max(0.0D, radius);
        this.batchSize = Math.max(1, batchSize);
        this.intervalTicks = Math.max(1, intervalTicks);
        this.maxAlive = Math.max(1, maxAlive);
        this.level = Math.max(1.0D, level);
        this.activationRange = Math.max(1.0D, activationRange);
        this.enabled = enabled;
    }

    public String id() {
        return id;
    }

    public Location location() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public String rule() {
        return rule;
    }

    public int amount() {
        return amount;
    }

    public double radius() {
        return radius;
    }

    public int batchSize() {
        return batchSize;
    }

    public int intervalTicks() {
        return intervalTicks;
    }

    public int maxAlive() {
        return maxAlive;
    }

    public double level() {
        return level;
    }

    public double activationRange() {
        return activationRange;
    }

    public boolean enabled() {
        return enabled;
    }

    public void set(String key, String value) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "rule" -> this.rule = value;
            case "amount" -> this.amount = Math.max(1, Integer.parseInt(value));
            case "radius" -> this.radius = Math.max(0.0D, Double.parseDouble(value));
            case "batch", "batch-size" -> this.batchSize = Math.max(1, Integer.parseInt(value));
            case "interval", "interval-ticks" -> this.intervalTicks = Math.max(1, Integer.parseInt(value));
            case "maxalive", "max-alive" -> this.maxAlive = Math.max(1, Integer.parseInt(value));
            case "level" -> this.level = Math.max(1.0D, Double.parseDouble(value));
            case "activation", "activation-range" -> this.activationRange = Math.max(1.0D, Double.parseDouble(value));
            case "enabled" -> this.enabled = Boolean.parseBoolean(value);
            default -> throw new IllegalArgumentException("Unknown spawn attribute: " + key);
        }
    }
}

package net.danh.clientcore.storage;

import java.util.UUID;

public record PlayerStats(UUID uuid, String name, double luck, boolean excludedFromTop) {
    public PlayerStats withLuck(double value) {
        return new PlayerStats(uuid, name, Math.max(0.0D, value), excludedFromTop);
    }

    public PlayerStats withName(String value) {
        return new PlayerStats(uuid, value == null || value.isBlank() ? name : value, luck, excludedFromTop);
    }

    public PlayerStats withExcludedFromTop(boolean value) {
        return new PlayerStats(uuid, name, luck, value);
    }
}

package net.danh.clientcore.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

import java.util.Locale;

final class RegistryAccess {
    private RegistryAccess() {
    }

    static Attribute attribute(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        NamespacedKey key = normalized.contains(":") ? NamespacedKey.fromString(normalized) : NamespacedKey.minecraft(normalized);
        if (key == null) {
            return null;
        }
        Registry<Attribute> registry = Bukkit.getRegistry(Attribute.class);
        return registry == null ? null : registry.get(key);
    }
}

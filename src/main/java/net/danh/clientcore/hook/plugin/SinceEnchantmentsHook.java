package net.danh.clientcore.hook.plugin;

import net.danh.sinceenchantments.SinceEnchantments;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

public final class SinceEnchantmentsHook {
    private SinceEnchantmentsHook() {
    }

    public static int enchantLevel(SinceEnchantments plugin, ItemStack item, String enchantId) {
        if (plugin == null || item == null || item.isEmpty() || enchantId == null || enchantId.isBlank()) {
            return 0;
        }
        return levelFromMap(plugin.getEnchantManager().getAllEnchantsOnItem(item), enchantId);
    }

    private static int levelFromMap(Map<?, ?> enchants, String enchantId) {
        String normalized = normalize(enchantId);
        for (Map.Entry<?, ?> entry : enchants.entrySet()) {
            if (!(entry.getValue() instanceof Number level)) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            if (normalize(key).equals(normalized) || normalize(unprefixed(key)).equals(normalized)) {
                return Math.max(0, level.intValue());
            }
        }
        return 0;
    }

    private static String unprefixed(String input) {
        int colon = input.indexOf(':');
        return colon >= 0 && colon + 1 < input.length() ? input.substring(colon + 1) : input;
    }

    private static String normalize(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }
}

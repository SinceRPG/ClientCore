package net.danh.clientcore.mob;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

final class MobRuleLoader {
    private final Plugin plugin;

    MobRuleLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    List<MobRule> load() {
        List<MobRule> rules = new ArrayList<>();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("client-mobs.rules");
        if (root == null) {
            return rules;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            EntityType fallback = EntityType.ZOMBIE;
            try {
                fallback = EntityType.valueOf(section.getString("fallback-entity", "ZOMBIE").toUpperCase());
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown fallback entity for client mob rule " + id);
            }
            List<MobVariant> variants = new ArrayList<>();
            for (var value : section.getMapList("variants")) {
                ConfigurationSection variant = section.createSection("__variant_" + variants.size(), value);
                EntityType variantFallback = fallback;
                try {
                    variantFallback = EntityType.valueOf(variant.getString("fallback-entity", fallback.name()).toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown fallback entity for client mob rule variant " + id + ":" + variants.size());
                }
                variants.add(new MobVariant(
                        variant.getString("id", "variant_" + variants.size()),
                        variant.getString("mythicmob-id", section.getString("mythicmob-id", id)),
                        variantFallback,
                        Math.max(0.0D, variant.getDouble("weight", 1.0D)),
                        variant.getBoolean("rare", false),
                        Math.max(0.0D, variant.getDouble("luck-multiplier", 1.0D)),
                        new HashSet<>(variant.getStringList("required-condition-ids")),
                        Math.max(1.0D, variant.getDouble("health", section.getDouble("health", 20.0D))),
                        Math.max(0.0D, variant.getDouble("damage", section.getDouble("damage", 2.0D)))
                ));
            }
            if (variants.isEmpty()) {
                variants.add(new MobVariant(
                        "default",
                        section.getString("mythicmob-id", id),
                        fallback,
                        Math.max(0.0D, section.getDouble("weight", 1.0D)),
                        section.getBoolean("rare", false),
                        Math.max(0.0D, section.getDouble("luck-multiplier", 1.0D)),
                        new HashSet<>(section.getStringList("required-condition-ids")),
                        Math.max(1.0D, section.getDouble("health", 20.0D)),
                        Math.max(0.0D, section.getDouble("damage", 2.0D))
                ));
            }
            rules.add(new MobRule(
                    id,
                    section.getInt("priority", 0),
                    section.getString("condition", ""),
                    section.getStringList("conditions"),
                    variants
            ));
        }
        rules.sort(Comparator.comparingInt(MobRule::priority).reversed());
        return rules;
    }
}

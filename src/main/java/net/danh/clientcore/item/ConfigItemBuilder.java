package net.danh.clientcore.item;

import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigItemBuilder {
    private final Plugin plugin;
    private final HookRegistry hooks;

    public ConfigItemBuilder(Plugin plugin, HookRegistry hooks) {
        this.plugin = plugin;
        this.hooks = hooks;
    }

    public ItemStack build(Player player, ConfigurationSection section) {
        if (section == null) {
            return ItemStack.empty();
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null || material.isAir()) {
            material = Material.STONE;
        }
        ItemStack item = ItemStack.of(material, Math.max(1, section.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = section.getString("name");
            if (name != null && !name.isBlank()) {
                meta.customName(Text.mm(hooks.placeholders(player, name)));
            }
            String itemName = section.getString("item-name");
            if (itemName != null && !itemName.isBlank()) {
                meta.itemName(Text.mm(hooks.placeholders(player, itemName)));
            }
            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(line -> Text.mm(hooks.placeholders(player, line))).toList());
            }
            if (section.contains("custom-model-data")) {
                int value = section.getInt("custom-model-data");
                if (value > 0) {
                    meta.setCustomModelData(value);
                }
            }
            if (section.contains("item-model")) {
                meta.setItemModel(key(section.getString("item-model")));
            }
            if (section.contains("tooltip-style")) {
                meta.setTooltipStyle(key(section.getString("tooltip-style")));
            }
            if (section.contains("hide-tooltip")) {
                meta.setHideTooltip(section.getBoolean("hide-tooltip", false));
            }
            if (section.contains("enchantment-glint")) {
                meta.setEnchantmentGlintOverride(section.isSet("enchantment-glint") ? section.getBoolean("enchantment-glint") : null);
            }
            if (section.contains("glider")) {
                meta.setGlider(section.getBoolean("glider", false));
            }
            if (section.contains("fire-resistant")) {
                meta.setFireResistant(section.getBoolean("fire-resistant", false));
            }
            if (section.contains("max-stack-size")) {
                meta.setMaxStackSize(Math.max(1, Math.min(99, section.getInt("max-stack-size"))));
            }
            if (section.contains("rarity")) {
                try {
                    meta.setRarity(ItemRarity.valueOf(section.getString("rarity", "COMMON").toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown item rarity: " + section.getString("rarity"));
                }
            }
            if (section.contains("enchantable")) {
                meta.setEnchantable(Math.max(1, section.getInt("enchantable")));
            }
            meta.setUnbreakable(section.getBoolean("unbreakable", false));
            for (String flag : section.getStringList("hide-flags")) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown item flag: " + flag);
                }
            }
            ConfigurationSection enchants = section.getConfigurationSection("enchants");
            if (enchants != null) {
                for (String key : enchants.getKeys(false)) {
                    Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                    if (enchantment != null) {
                        meta.addEnchant(enchantment, enchants.getInt(key, 1), true);
                    }
                }
            }
            ConfigurationSection attributes = section.getConfigurationSection("attributes");
            if (attributes != null) {
                addAttributes(meta, attributes);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public List<ItemStack> buildAll(Player player, List<ConfigurationSection> sections) {
        List<ItemStack> items = new ArrayList<>();
        for (ConfigurationSection section : sections) {
            ItemStack item = build(player, section);
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private void addAttributes(ItemMeta meta, ConfigurationSection attributes) {
        for (String key : attributes.getKeys(false)) {
            Attribute attribute = RegistryAccess.attribute(key);
            ConfigurationSection section = attributes.getConfigurationSection(key);
            if (attribute == null || section == null) {
                continue;
            }
            double amount = section.getDouble("amount", 0.0D);
            AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(section.getString("operation", "ADD_NUMBER").toUpperCase(Locale.ROOT));
            EquipmentSlotGroup slot = EquipmentSlotGroup.getByName(section.getString("slot", "ANY").toLowerCase(Locale.ROOT));
            if (slot == null) {
                slot = EquipmentSlotGroup.ANY;
            }
            NamespacedKey modifierKey = new NamespacedKey(plugin, key.toLowerCase(Locale.ROOT).replace('.', '_').replace(':', '_'));
            meta.addAttributeModifier(attribute, new AttributeModifier(modifierKey, amount, operation, slot));
        }
    }

    private static NamespacedKey key(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        NamespacedKey parsed = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        return parsed == null ? NamespacedKey.minecraft(raw.toLowerCase(Locale.ROOT)) : parsed;
    }
}

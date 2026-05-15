package net.danh.clientcore.hook.plugin;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public final class MMOItemsHook {
    public static Optional<ItemStack> getItem(String typeId, String itemId) {
        Type type = Type.get(typeId);
        if (type == null) return Optional.empty();
        return Optional.ofNullable(MMOItems.plugin.getItem(type, itemId));
    }

    public static List<String> getTypes() {
        return MMOItems.plugin.getTypes().getAll().stream().map(Type::getId).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static List<String> getItems(String typeId) {
        Type type = Type.get(typeId);
        if (type == null) return List.of();
        return MMOItems.plugin.getTemplates().getTemplates(type).stream().map(MMOItemTemplate::getId).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
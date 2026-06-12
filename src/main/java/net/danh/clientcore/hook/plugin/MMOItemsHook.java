package net.danh.clientcore.hook.plugin;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
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

    public static boolean matches(ItemStack item, String typeId, String itemId) {
        if (item == null || item.getType().isAir() || typeId == null || itemId == null) return false;
        String typeName = MMOItems.getTypeName(item);
        String id = MMOItems.getID(item);
        if (matches(typeName, typeId) && matches(id, itemId)) {
            return true;
        }
        try {
            LiveMMOItem live = new LiveMMOItem(item);
            return live.getType() != null
                    && live.getType().getId().equalsIgnoreCase(typeId)
                    && live.getId() != null
                    && live.getId().equalsIgnoreCase(itemId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static String describe(ItemStack item) {
        if (item == null || item.getType().isAir()) return "";
        String typeName = MMOItems.getTypeName(item);
        String id = MMOItems.getID(item);
        if (typeName != null && id != null) {
            return typeName + "/" + id;
        }
        try {
            LiveMMOItem live = new LiveMMOItem(item);
            if (live.getType() != null && live.getId() != null) {
                return live.getType().getId() + "/" + live.getId();
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    private static boolean matches(String actual, String expected) {
        return actual != null && expected != null && actual.equalsIgnoreCase(expected);
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

package net.danh.clientcore.luck;

import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.item.ConfigItemBuilder;
import net.danh.clientcore.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class LuckItemService implements Listener {
    private final Plugin plugin;
    private final LuckService luck;
    private final ConfigItemBuilder itemBuilder;
    private final NamespacedKey amountKey;

    public LuckItemService(Plugin plugin, LuckService luck, HookRegistry hooks) {
        this.plugin = plugin;
        this.luck = luck;
        this.itemBuilder = new ConfigItemBuilder(plugin, hooks);
        this.amountKey = new NamespacedKey(plugin, "luck_item_amount");
    }

    public ItemStack build(Player viewer, int amount) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("luck.item");
        ItemStack item = itemBuilder.build(viewer, section);
        if (item.isEmpty()) {
            return item;
        }
        item.editMeta(meta -> meta.getPersistentDataContainer().set(amountKey, PersistentDataType.INTEGER, Math.max(0, amount)));
        return item;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        Integer amount = item.getItemMeta().getPersistentDataContainer().get(amountKey, PersistentDataType.INTEGER);
        if (amount == null || amount <= 0) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        luck.add(player.getUniqueId(), player.getName(), amount);
        item.subtract();
        Text.send(player, plugin.getConfig().getString("messages.luck-item-used", "<green>You gained <white>%amount%</white> luck.").replace("%amount%", String.valueOf(amount)));
    }
}

package net.danh.clientcore.gui;

import net.danh.clientcore.ClientCore;
import net.danh.clientcore.block.BlockRegenService;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.mob.ClientMobService;
import net.danh.clientcore.mob.SpawnPoint;
import net.danh.clientcore.util.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class EditorMenu implements Listener {
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final ClientCore plugin;
    private final BlockRegenService blocks;
    private final ClientMobService mobs;
    private final HookRegistry hooks;
    private final List<BlockChoice> blockChoices;
    private final List<Material> itemChoices;
    private final List<EntityType> entityChoices;

    public EditorMenu(ClientCore plugin, BlockRegenService blocks, ClientMobService mobs, HookRegistry hooks) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.mobs = mobs;
        this.hooks = hooks;
        this.blockChoices = buildBlockChoices();
        this.itemChoices = buildItemChoices();
        this.entityChoices = buildEntityChoices();
    }

    public void openMain(Player player) {
        Inventory inv = menu(View.MAIN, "", 0, title("main", "ClientCore Editor"));
        inv.setItem(10, button("main.blocks", Material.GRASS_BLOCK, "<green>Block Rules", List.of("<gray>Edit fake block regen rules.")));
        inv.setItem(12, button("main.mobs", Material.ZOMBIE_HEAD, "<green>Mob Rules", List.of("<gray>Edit client MythicMob rules.")));
        inv.setItem(14, button("main.spawns", Material.SPAWNER, "<green>Spawn Points", List.of("<gray>Edit fixed spawn locations.")));
        inv.setItem(16, button("main.vanilla_items", Material.CHEST, "<green>Vanilla Drop Items", List.of("<gray>Edit material for vanilla drops.")));
        inv.setItem(22, button("main.reload", Material.LIME_DYE, "<green>Save & Reload", List.of("<gray>Apply all configuration changes.")));
        player.openInventory(inv);
    }

    private void openBlockRules(Player player, int page) {
        List<String> ids = configKeys("block-regen.rules");
        Inventory inv = pageMenu(View.BLOCK_RULES, "", page, title("blocks", "Block Rules"));
        inv.setItem(4, button("list.add", Material.EMERALD, "<green>Add Block Rule", List.of("<yellow>Click to create a rule")));
        paginate(inv, ids, page, id -> {
            ConfigurationSection s = plugin.getConfig().getConfigurationSection("block-regen.rules." + id);
            return button("block.rule", s.getBoolean("enabled", true) ? Material.LIME_DYE : Material.GRAY_DYE, "<aqua>" + id, List.of(
                    "<gray>Display: <white>" + s.getString("display-block", "STONE"),
                    "<gray>Sources: <white>" + s.getStringList("source-blocks"),
                    "<gray>Regen: <white>" + s.getInt("regen-ticks", 100),
                    "<yellow>Left: edit rule",
                    "<yellow>Shift left: toggle",
                    "<yellow>Shift right: delete"
            ));
        });
        player.openInventory(inv);
    }

    private void openBlockRule(Player player, String id) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("block-regen.rules." + id);
        if (s == null) {
            openBlockRules(player, 0);
            return;
        }
        Inventory inv = menu(View.BLOCK_RULE, id, 0, title("block-rule", "Block Rule: " + id));
        inv.setItem(10, button("field.enabled", s.getBoolean("enabled", true) ? Material.LIME_DYE : Material.GRAY_DYE, "<green>Enabled", List.of("<white>" + s.getBoolean("enabled", true), "<yellow>Click to toggle")));
        inv.setItem(12, button("field.display", displayMaterial(s.getString("display-block", "STONE")), "<green>Display Block", List.of("<white>" + s.getString("display-block", "STONE"), "<yellow>Click to choose from all Minecraft blocks")));
        inv.setItem(14, button("field.source", Material.STONE, "<green>Source Blocks", List.of("<white>" + s.getStringList("source-blocks"), "<yellow>Click to manage source blocks")));
        inv.setItem(16, button("field.regen", Material.CLOCK, "<green>Regen Ticks", List.of("<white>" + s.getInt("regen-ticks", 100), "<yellow>Left: -20, Right: +20")));
        inv.setItem(28, button("field.drop", Material.CHEST, "<green>Drops", List.of("<gray>Edit vanilla/MMOItems drops.", "<yellow>Click to open drops")));
        inv.setItem(30, button("field.condition", Material.COMPARATOR, "<green>Conditions", List.of("<white>" + s.getStringList("conditions"), "<yellow>Click to edit conditions")));
        inv.setItem(32, button("field.variants", Material.AMETHYST_SHARD, "<green>Weighted Variants", List.of("<white>" + s.getMapList("variants").size() + " configured", "<yellow>Click to edit weights/variants")));
        inv.setItem(49, back());
        player.openInventory(inv);
    }

    private void openSourceBlocks(Player player, String id, int page) {
        List<String> sources = plugin.getConfig().getStringList("block-regen.rules." + id + ".source-blocks");
        Inventory inv = pageMenu(View.SOURCE_BLOCKS, id, page, title("source-blocks", "Source Blocks: " + id));
        inv.setItem(4, button("list.add", Material.EMERALD, "<green>Add Source Block", List.of("<yellow>Click to choose from all Minecraft blocks")));
        paginate(inv, sources, page, material -> button("source.block", displayMaterial(material), "<aqua>" + material, List.of("<yellow>Shift right: remove")));
        player.openInventory(inv);
    }

    private void openMobRules(Player player, int page) {
        List<String> ids = configKeys("client-mobs.rules");
        Inventory inv = pageMenu(View.MOB_RULES, "", page, title("mobs", "Mob Rules"));
        inv.setItem(4, button("list.add", Material.EMERALD, "<green>Add Mob Rule", List.of("<yellow>Click to create a rule")));
        paginate(inv, ids, page, id -> {
            ConfigurationSection s = plugin.getConfig().getConfigurationSection("client-mobs.rules." + id);
            return button("mob.rule", s.getBoolean("enabled", true) ? Material.ZOMBIE_HEAD : Material.SKELETON_SKULL, "<aqua>" + id, List.of(
                    "<gray>MythicMob: <white>" + s.getString("mythicmob-id", id),
                    "<gray>Fallback: <white>" + s.getString("fallback-entity", "ZOMBIE"),
                    "<gray>Health: <white>" + s.getDouble("health", 20.0),
                    "<gray>Damage: <white>" + s.getDouble("damage", 2.0),
                    "<yellow>Left: edit rule",
                    "<yellow>Shift left: toggle",
                    "<yellow>Shift right: delete"
            ));
        });
        player.openInventory(inv);
    }

    private void openMobRule(Player player, String id) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("client-mobs.rules." + id);
        if (s == null) {
            openMobRules(player, 0);
            return;
        }
        Inventory inv = menu(View.MOB_RULE, id, 0, title("mob-rule", "Mob Rule: " + id));
        inv.setItem(10, button("field.enabled", s.getBoolean("enabled", true) ? Material.LIME_DYE : Material.GRAY_DYE, "<green>Enabled", List.of("<white>" + s.getBoolean("enabled", true), "<yellow>Click to toggle")));
        inv.setItem(12, button("field.mythicmob", Material.NAME_TAG, "<green>MythicMob", List.of("<white>" + s.getString("mythicmob-id", id), "<yellow>Click to select from MythicMobs")));
        inv.setItem(14, button("field.fallback", fallbackMaterial(s.getString("fallback-entity", "ZOMBIE")), "<green>Fallback Entity", List.of("<white>" + s.getString("fallback-entity", "ZOMBIE"), "<yellow>Click to select entity type")));
        inv.setItem(16, button("field.health", Material.RED_DYE, "<green>Health", List.of("<white>" + s.getDouble("health", 20.0), "<yellow>Left: -5, Right: +5")));
        inv.setItem(28, button("field.damage", Material.IRON_SWORD, "<green>Damage", List.of("<white>" + s.getDouble("damage", 2.0), "<yellow>Left: -1, Right: +1")));
        inv.setItem(30, button("field.condition", Material.COMPARATOR, "<green>Conditions", List.of("<white>" + s.getStringList("conditions"), "<yellow>Click to edit conditions")));
        inv.setItem(32, button("field.variants", Material.AMETHYST_SHARD, "<green>Weighted Variants", List.of("<white>" + s.getMapList("variants").size() + " configured", "<yellow>Click to edit weights/variants")));
        inv.setItem(49, back());
        player.openInventory(inv);
    }

    private void openConditions(Player player, String context, int page) {
        String path = conditionPath(context);
        List<String> lines = plugin.getConfig().getStringList(path);
        Inventory inv = pageMenu(View.CONDITIONS, context, page, title("conditions", "Conditions: " + context));
        inv.setItem(4, button("list.add", Material.EMERALD, "<green>Add Condition", List.of("<yellow>Adds: condition_%n%;%mmocore_level%;>=;1;optional")));
        paginate(inv, lines, page, line -> button("condition.line", Material.COMPARATOR, "<aqua>" + line, List.of(
                "<yellow>Left: toggle optional",
                "<yellow>Right: cycle operator",
                "<yellow>Shift right: remove",
                "<gray>Format: id;placeholder;operator;value;optional"
        )));
        player.openInventory(inv);
    }

    private void openBlockVariants(Player player, String id, int page) {
        List<Map<?, ?>> variants = plugin.getConfig().getMapList("block-regen.rules." + id + ".variants");
        Inventory inv = pageMenu(View.BLOCK_VARIANTS, id, page, title("block-variants", "Block Variants: " + id));
        inv.setItem(4, button("list.add", Material.EMERALD, "<green>Add Block Variant", List.of("<yellow>Click to add weighted block variant")));
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < variants.size(); i++) indexes.add(i);
        paginate(inv, indexes, page, index -> {
            ConfigurationSection s = tempSection(variants.get(index));
            return button("variant.block", displayMaterial(s.getString("display-block", "STONE")), "<aqua>Variant #" + (index + 1), List.of(
                    "<gray>Id: <white>" + s.getString("id", "variant_" + index),
                    "<gray>Block: <white>" + s.getString("display-block", "STONE"),
                    "<gray>Weight: <white>" + s.getDouble("weight", 1.0D),
                    "<gray>Rare: <white>" + s.getBoolean("rare", false),
                    "<gray>Required IDs: <white>" + s.getStringList("required-condition-ids"),
                    "<yellow>Left: edit",
                    "<yellow>Shift right: remove"
            ));
        });
        player.openInventory(inv);
    }

    private void openBlockVariant(Player player, String context) {
        ConfigurationSection s = variantSection("block-regen.rules", context);
        Inventory inv = menu(View.BLOCK_VARIANT, context, 0, title("block-variant", "Block Variant"));
        inv.setItem(10, button("variant.display", displayMaterial(s.getString("display-block", "STONE")), "<green>Display Block", List.of("<white>" + s.getString("display-block", "STONE"), "<yellow>Click to choose block")));
        inv.setItem(12, button("variant.weight", Material.SCAFFOLDING, "<green>Weight", List.of("<white>" + s.getDouble("weight", 1.0D), "<yellow>Left: -1, Right: +1")));
        inv.setItem(14, button("variant.rare", s.getBoolean("rare", false) ? Material.NETHER_STAR : Material.GRAY_DYE, "<green>Rare", List.of("<white>" + s.getBoolean("rare", false), "<yellow>Click to toggle")));
        inv.setItem(16, button("variant.luck", Material.RABBIT_FOOT, "<green>Luck Multiplier", List.of("<white>" + s.getDouble("luck-multiplier", 1.0D), "<yellow>Left: -0.1, Right: +0.1")));
        inv.setItem(28, button("variant.regen", Material.CLOCK, "<green>Regen Ticks", List.of("<white>" + s.getInt("regen-ticks", 100), "<yellow>Left: -20, Right: +20")));
        inv.setItem(30, button("variant.required", Material.COMPARATOR, "<green>Required Optional Condition IDs", List.of("<white>" + s.getStringList("required-condition-ids"), "<yellow>Click to cycle IDs from rule conditions")));
        inv.setItem(49, back());
        player.openInventory(inv);
    }

    private void openMobVariants(Player player, String id, int page) {
        List<Map<?, ?>> variants = plugin.getConfig().getMapList("client-mobs.rules." + id + ".variants");
        Inventory inv = pageMenu(View.MOB_VARIANTS, id, page, title("mob-variants", "Mob Variants: " + id));
        inv.setItem(4, button("list.add", Material.EMERALD, "<green>Add Mob Variant", List.of("<yellow>Click to add weighted mob variant")));
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < variants.size(); i++) indexes.add(i);
        paginate(inv, indexes, page, index -> {
            ConfigurationSection s = tempSection(variants.get(index));
            return button("variant.mob", Material.ZOMBIE_HEAD, "<aqua>Variant #" + (index + 1), List.of(
                    "<gray>Id: <white>" + s.getString("id", "variant_" + index),
                    "<gray>MythicMob: <white>" + s.getString("mythicmob-id", ""),
                    "<gray>Weight: <white>" + s.getDouble("weight", 1.0D),
                    "<gray>Rare: <white>" + s.getBoolean("rare", false),
                    "<gray>Required IDs: <white>" + s.getStringList("required-condition-ids"),
                    "<yellow>Left: edit",
                    "<yellow>Shift right: remove"
            ));
        });
        player.openInventory(inv);
    }

    private void openMobVariant(Player player, String context) {
        ConfigurationSection s = variantSection("client-mobs.rules", context);
        Inventory inv = menu(View.MOB_VARIANT, context, 0, title("mob-variant", "Mob Variant"));
        inv.setItem(10, button("variant.mythic", Material.NAME_TAG, "<green>MythicMob", List.of("<white>" + s.getString("mythicmob-id", ""), "<yellow>Click to choose MythicMob")));
        inv.setItem(12, button("variant.fallback", fallbackMaterial(s.getString("fallback-entity", "ZOMBIE")), "<green>Fallback Entity", List.of("<white>" + s.getString("fallback-entity", "ZOMBIE"), "<yellow>Click to choose entity")));
        inv.setItem(14, button("variant.weight", Material.SCAFFOLDING, "<green>Weight", List.of("<white>" + s.getDouble("weight", 1.0D), "<yellow>Left: -1, Right: +1")));
        inv.setItem(16, button("variant.rare", s.getBoolean("rare", false) ? Material.NETHER_STAR : Material.GRAY_DYE, "<green>Rare", List.of("<white>" + s.getBoolean("rare", false), "<yellow>Click to toggle")));
        inv.setItem(28, button("variant.luck", Material.RABBIT_FOOT, "<green>Luck Multiplier", List.of("<white>" + s.getDouble("luck-multiplier", 1.0D), "<yellow>Left: -0.1, Right: +0.1")));
        inv.setItem(30, button("variant.health", Material.RED_DYE, "<green>Health", List.of("<white>" + s.getDouble("health", 20.0D), "<yellow>Left: -5, Right: +5")));
        inv.setItem(32, button("variant.damage", Material.IRON_SWORD, "<green>Damage", List.of("<white>" + s.getDouble("damage", 2.0D), "<yellow>Left: -1, Right: +1")));
        inv.setItem(34, button("variant.required", Material.COMPARATOR, "<green>Required Optional Condition IDs", List.of("<white>" + s.getStringList("required-condition-ids"), "<yellow>Click to cycle IDs from rule conditions")));
        inv.setItem(49, back());
        player.openInventory(inv);
    }

    private void openSpawns(Player player, int page) {
        List<String> ids = mobs.spawnIds();
        Inventory inv = pageMenu(View.SPAWNS, "", page, title("spawns", "Spawn Points"));
        inv.setItem(4, button("list.add", Material.EMERALD, "<green>Add Spawn Point", List.of("<yellow>Click to create at your current location")));
        paginate(inv, ids, page, id -> {
            SpawnPoint p = mobs.spawn(id).orElse(null);
            List<String> lore = p == null ? List.of() : new ArrayList<>(spawnLore(p));
            if (p != null) {
                lore.add("<yellow>Shift right: delete");
            }
            return button("spawn.point", p != null && p.enabled() ? Material.SPAWNER : Material.BARRIER, "<aqua>" + id, lore);
        });
        player.openInventory(inv);
    }

    private void openSpawn(Player player, String id) {
        SpawnPoint p = mobs.spawn(id).orElse(null);
        if (p == null) {
            openSpawns(player, 0);
            return;
        }
        Inventory inv = menu(View.SPAWN_DETAIL, id, 0, title("spawn", "Spawn: " + id));
        inv.setItem(10, button("field.enabled", p.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, "<green>Enabled", List.of("<white>" + p.enabled(), "<yellow>Click to toggle")));
        inv.setItem(12, button("field.rule", Material.NAME_TAG, "<green>Rule", List.of("<white>" + blank(p.rule()), "<yellow>Click to select mob rule")));
        inv.setItem(14, button("field.amount", Material.ZOMBIE_HEAD, "<green>Amount", List.of("<white>" + p.amount(), "<yellow>Left: -1, Right: +1")));
        inv.setItem(16, button("field.batch", Material.IRON_SWORD, "<green>Batch Size", List.of("<white>" + p.batchSize(), "<yellow>Left: -1, Right: +1")));
        inv.setItem(28, button("field.radius", Material.COMPASS, "<green>Radius", List.of("<white>" + p.radius(), "<yellow>Left: -1, Right: +1")));
        inv.setItem(30, button("field.interval", Material.CLOCK, "<green>Interval Ticks", List.of("<white>" + p.intervalTicks(), "<yellow>Left: -20, Right: +20")));
        inv.setItem(32, button("field.maxalive", Material.TOTEM_OF_UNDYING, "<green>Max Alive", List.of("<white>" + p.maxAlive(), "<yellow>Left: -1, Right: +1")));
        inv.setItem(34, button("field.activation", Material.ENDER_EYE, "<green>Activation Range", List.of("<white>" + p.activationRange(), "<yellow>Left: -4, Right: +4")));
        inv.setItem(49, back());
        player.openInventory(inv);
    }

    private void openDrops(Player player, String ruleId, int page) {
        ConfigurationSection rule = plugin.getConfig().getConfigurationSection("block-regen.rules." + ruleId);
        List<?> drops = rule == null ? List.of() : rule.getMapList("drops");
        Inventory inv = pageMenu(View.DROPS, ruleId, page, title("drops", "Drops: " + ruleId));
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < drops.size(); i++) {
            indexes.add(i);
        }
        paginate(inv, indexes, page, index -> {
            ConfigurationSection drop = dropSection(ruleId, index);
            String type = drop.getString("type", "vanilla");
            Material material = displayMaterial(drop.getString("material", "CHEST"));
            return button("drop.item", material, "<aqua>Drop #" + (index + 1), List.of(
                    "<gray>Type: <white>" + type,
                    "<gray>Material: <white>" + drop.getString("material", "AIR"),
                    "<gray>MMOItems: <white>" + drop.getString("mmo-type", "-") + ":" + drop.getString("mmo-id", "-"),
                    "<yellow>Left: edit",
                    "<yellow>Shift right: remove"
            ));
        });
        inv.setItem(4, button("drop.add", Material.EMERALD, "<green>Add Vanilla Drop", List.of("<yellow>Click to add")));
        player.openInventory(inv);
    }

    private void openDrop(Player player, String context) {
        String[] split = context.split(":", 2);
        String ruleId = split[0];
        int index = Integer.parseInt(split[1]);
        ConfigurationSection drop = dropSection(ruleId, index);
        Inventory inv = menu(View.DROP_DETAIL, context, 0, title("drop", "Drop #" + (index + 1)));
        inv.setItem(10, button("drop.type", drop.getString("type", "vanilla").equalsIgnoreCase("mmoitems") ? Material.NETHER_STAR : Material.CHEST, "<green>Type", List.of("<white>" + drop.getString("type", "vanilla"), "<yellow>Click to toggle vanilla/MMOItems")));
        inv.setItem(12, button("drop.material", displayMaterial(drop.getString("material", "STONE")), "<green>Vanilla Material", List.of("<white>" + drop.getString("material", "STONE"), "<yellow>Click to choose material")));
        inv.setItem(14, button("drop.amount", Material.PAPER, "<green>Amount", List.of("<white>" + drop.getInt("amount", 1), "<yellow>Left: -1, Right: +1")));
        inv.setItem(16, button("drop.name", Material.NAME_TAG, "<green>Custom Name", List.of("<white>" + drop.getString("name", ""), "<gray>Edit text in config or command.")));
        inv.setItem(19, button("drop.unbreakable", drop.getBoolean("unbreakable", false) ? Material.LIME_DYE : Material.GRAY_DYE, "<green>Unbreakable", List.of("<white>" + drop.getBoolean("unbreakable", false), "<yellow>Click to toggle")));
        inv.setItem(20, button("drop.hide-tooltip", drop.getBoolean("hide-tooltip", false) ? Material.LIME_DYE : Material.GRAY_DYE, "<green>Hide Tooltip", List.of("<white>" + drop.getBoolean("hide-tooltip", false), "<yellow>Click to toggle")));
        inv.setItem(21, button("drop.glint", glintIcon(drop), "<green>Enchantment Glint", List.of("<white>" + drop.getString("enchantment-glint", "default"), "<yellow>Click to cycle default/true/false")));
        inv.setItem(22, button("drop.glider", drop.getBoolean("glider", false) ? Material.ELYTRA : Material.GRAY_DYE, "<green>Glider", List.of("<white>" + drop.getBoolean("glider", false), "<yellow>Click to toggle")));
        inv.setItem(23, button("drop.fire-resistant", drop.getBoolean("fire-resistant", false) ? Material.BLAZE_POWDER : Material.GRAY_DYE, "<green>Fire Resistant", List.of("<white>" + drop.getBoolean("fire-resistant", false), "<yellow>Click to toggle")));
        inv.setItem(24, button("drop.custom-model-data", Material.ITEM_FRAME, "<green>Custom Model Data", List.of("<white>" + drop.getInt("custom-model-data", 0), "<yellow>Left: -1, Right: +1", "<yellow>Shift right: clear")));
        inv.setItem(25, button("drop.max-stack-size", Material.BUNDLE, "<green>Max Stack Size", List.of("<white>" + drop.getInt("max-stack-size", 0), "<yellow>Left: -1, Right: +1", "<yellow>Shift right: clear")));
        inv.setItem(28, button("drop.rarity", rarityIcon(drop.getString("rarity", "COMMON")), "<green>Rarity", List.of("<white>" + drop.getString("rarity", "COMMON"), "<yellow>Click to cycle")));
        inv.setItem(29, button("drop.enchantable", Material.EXPERIENCE_BOTTLE, "<green>Enchantable", List.of("<white>" + drop.getInt("enchantable", 0), "<yellow>Left: -1, Right: +1", "<yellow>Shift right: clear")));
        inv.setItem(30, button("drop.mmo-type", Material.BOOK, "<green>MMOItems Type", List.of("<white>" + drop.getString("mmo-type", ""), "<yellow>Click to choose type")));
        inv.setItem(32, button("drop.mmo-id", Material.ENCHANTED_BOOK, "<green>MMOItems Item", List.of("<white>" + drop.getString("mmo-id", ""), "<yellow>Click to choose item from selected type")));
        inv.setItem(34, button("drop.text-meta", Material.WRITABLE_BOOK, "<green>Text Meta", List.of(
                "<gray>Config keys:",
                "<white>name, item-name, lore",
                "<white>item-model, tooltip-style",
                "<white>hide-flags, enchants, attributes"
        )));
        inv.setItem(49, back());
        player.openInventory(inv);
    }

    private <T> void openSelector(Player player, View view, String context, int page, String title, List<T> values, java.util.function.Function<T, ItemStack> renderer) {
        Inventory inv = pageMenu(view, context, page, title);
        paginate(inv, values, page, renderer);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }
        if (event.getRawSlot() == 45 && h.page > 0) {
            openPaged(player, h.view, h.id, h.page - 1);
            return;
        }
        if (event.getRawSlot() == 49) {
            openBack(player, h);
            return;
        }
        if (event.getRawSlot() == 53) {
            openPaged(player, h.view, h.id, h.page + 1);
            return;
        }
        switch (h.view) {
            case MAIN -> clickMain(player, event.getRawSlot());
            case BLOCK_RULES -> clickBlockRules(player, event);
            case BLOCK_RULE -> clickBlockRule(player, h.id, event);
            case SOURCE_BLOCKS -> clickSourceBlocks(player, h.id, event);
            case CONDITIONS -> clickConditions(player, h.id, event);
            case BLOCK_VARIANTS -> clickBlockVariants(player, h.id, event);
            case BLOCK_VARIANT -> clickBlockVariant(player, h.id, event);
            case MOB_RULES -> clickMobRules(player, event);
            case MOB_RULE -> clickMobRule(player, h.id, event);
            case MOB_VARIANTS -> clickMobVariants(player, h.id, event);
            case MOB_VARIANT -> clickMobVariant(player, h.id, event);
            case SPAWNS -> clickSpawns(player, event);
            case SPAWN_DETAIL -> clickSpawn(player, h.id, event);
            case DROPS -> clickDrops(player, h.id, event);
            case DROP_DETAIL -> clickDrop(player, h.id, event);
            case SELECT_BLOCK -> selectBlock(player, h.id, event);
            case SELECT_ITEM -> selectItem(player, h.id, event);
            case SELECT_MYTHIC -> selectMythic(player, h.id, event);
            case SELECT_ENTITY -> selectEntity(player, h.id, event);
            case SELECT_MOB_RULE -> selectMobRule(player, h.id, event);
            case SELECT_MMO_TYPE -> selectMmoType(player, h.id, event);
            case SELECT_MMO_ITEM -> selectMmoItem(player, h.id, event);
        }
    }

    private void clickMain(Player player, int slot) {
        switch (slot) {
            case 10 -> openBlockRules(player, 0);
            case 12 -> openMobRules(player, 0);
            case 14 -> openSpawns(player, 0);
            case 16 -> openVanillaDropBrowser(player, 0);
            case 22 -> {
                plugin.reloadPlugin();
                Text.send(player, "<green>ClientCore reloaded.");
                openMain(player);
            }
            default -> {
            }
        }
    }

    private void clickBlockRules(Player player, InventoryClickEvent event) {
        if (event.getRawSlot() == 4) {
            String id = nextId("block-regen.rules", "block_rule");
            ConfigurationSection section = plugin.getConfig().createSection("block-regen.rules." + id);
            section.set("enabled", true);
            section.set("worlds", List.of(player.getWorld().getName()));
            section.set("source-blocks", List.of("STONE"));
            section.set("display-block", "STONE");
            section.set("condition", "");
            section.set("priority", 0);
            section.set("regen-ticks", 100);
            section.set("grow-animation.enabled", true);
            section.set("grow-animation.frames", 12);
            section.set("drops", List.of(Map.of("type", "vanilla", "material", "COBBLESTONE", "amount", 1)));
            saveReload();
            openBlockRule(player, id);
            return;
        }
        String id = itemName(event.getCurrentItem());
        if (id.isBlank()) return;
        if (event.isShiftClick() && event.isRightClick()) {
            plugin.getConfig().set("block-regen.rules." + id, null);
            saveReload();
            openBlockRules(player, holder(event).page);
        } else if (event.isShiftClick()) {
            toggleConfig("block-regen.rules." + id + ".enabled");
            saveReload();
            openBlockRules(player, holder(event).page);
        } else {
            openBlockRule(player, id);
        }
    }

    private void clickBlockRule(Player player, String id, InventoryClickEvent event) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("block-regen.rules." + id);
        if (s == null) return;
        switch (event.getRawSlot()) {
            case 10 -> {
                toggleConfig("block-regen.rules." + id + ".enabled");
                saveReload();
                openBlockRule(player, id);
            }
            case 12 -> openSelector(player, View.SELECT_BLOCK, "display:" + id, 0, "Choose Display Block", blockChoices, this::blockItem);
            case 14 -> openSourceBlocks(player, id, 0);
            case 16 -> {
                s.set("regen-ticks", Math.max(1, s.getInt("regen-ticks", 100) + (event.isRightClick() ? 20 : -20)));
                saveReload();
                openBlockRule(player, id);
            }
            case 28 -> openDrops(player, id, 0);
            case 30 -> openConditions(player, "block:" + id, 0);
            case 32 -> openBlockVariants(player, id, 0);
            default -> {
            }
        }
    }

    private void clickSourceBlocks(Player player, String id, InventoryClickEvent event) {
        if (event.getRawSlot() == 4) {
            openSelector(player, View.SELECT_BLOCK, "source:" + id, 0, "Add Source Block", blockChoices, this::blockItem);
            return;
        }
        if (!(event.isShiftClick() && event.isRightClick())) {
            return;
        }
        String material = itemName(event.getCurrentItem());
        if (material.isBlank()) {
            return;
        }
        List<String> blocks = new ArrayList<>(plugin.getConfig().getStringList("block-regen.rules." + id + ".source-blocks"));
        blocks.removeIf(value -> value.equalsIgnoreCase(material));
        plugin.getConfig().set("block-regen.rules." + id + ".source-blocks", blocks);
        saveReload();
        openSourceBlocks(player, id, holder(event).page);
    }

    private void clickConditions(Player player, String context, InventoryClickEvent event) {
        String path = conditionPath(context);
        List<String> lines = new ArrayList<>(plugin.getConfig().getStringList(path));
        if (event.getRawSlot() == 4) {
            lines.add("condition_" + (lines.size() + 1) + ";%mmocore_level%;>=;1;optional");
            plugin.getConfig().set(path, lines);
            saveReload();
            openConditions(player, context, holder(event).page);
            return;
        }
        String line = itemName(event.getCurrentItem());
        int index = lines.indexOf(line);
        if (index < 0) {
            return;
        }
        if (event.isShiftClick() && event.isRightClick()) {
            lines.remove(index);
        } else if (event.isRightClick()) {
            lines.set(index, cycleConditionOperator(line));
        } else {
            lines.set(index, toggleOptional(line));
        }
        plugin.getConfig().set(path, lines);
        saveReload();
        openConditions(player, context, holder(event).page);
    }

    private void clickBlockVariants(Player player, String rule, InventoryClickEvent event) {
        if (event.getRawSlot() == 4) {
            List<Map<?, ?>> variants = new ArrayList<>(plugin.getConfig().getMapList("block-regen.rules." + rule + ".variants"));
            variants.add(new LinkedHashMap<>(Map.of("id", "variant_" + (variants.size() + 1), "display-block", "STONE", "weight", 1.0D, "rare", false, "luck-multiplier", 1.0D, "regen-ticks", 100)));
            plugin.getConfig().set("block-regen.rules." + rule + ".variants", variants);
            saveReload();
            openBlockVariants(player, rule, holder(event).page);
            return;
        }
        int index = variantIndex(event.getCurrentItem());
        if (index < 0) return;
        if (event.isShiftClick() && event.isRightClick()) {
            removeVariant("block-regen.rules", rule, index);
            openBlockVariants(player, rule, holder(event).page);
        } else {
            openBlockVariant(player, rule + ":" + index);
        }
    }

    private void clickBlockVariant(Player player, String context, InventoryClickEvent event) {
        ConfigurationSection s = variantSection("block-regen.rules", context);
        switch (event.getRawSlot()) {
            case 10 -> {
                openSelector(player, View.SELECT_BLOCK, "blockvar:" + context, 0, "Choose Variant Block", blockChoices, this::blockItem);
                return;
            }
            case 12 -> setVariantValue("block-regen.rules", context, "weight", Math.max(0.0D, s.getDouble("weight", 1.0D) + (event.isRightClick() ? 1.0D : -1.0D)));
            case 14 -> setVariantValue("block-regen.rules", context, "rare", !s.getBoolean("rare", false));
            case 16 -> setVariantValue("block-regen.rules", context, "luck-multiplier", Math.max(0.0D, s.getDouble("luck-multiplier", 1.0D) + (event.isRightClick() ? 0.1D : -0.1D)));
            case 28 -> setVariantValue("block-regen.rules", context, "regen-ticks", Math.max(1, s.getInt("regen-ticks", 100) + (event.isRightClick() ? 20 : -20)));
            case 30 -> cycleRequiredConditionId("block-regen.rules", context, "block:" + context.split(":", 2)[0]);
            default -> {
                return;
            }
        }
        saveReload();
        openBlockVariant(player, context);
    }

    private void clickMobVariants(Player player, String rule, InventoryClickEvent event) {
        if (event.getRawSlot() == 4) {
            List<Map<?, ?>> variants = new ArrayList<>(plugin.getConfig().getMapList("client-mobs.rules." + rule + ".variants"));
            variants.add(new LinkedHashMap<>(Map.of("id", "variant_" + (variants.size() + 1), "mythicmob-id", rule, "fallback-entity", "ZOMBIE", "weight", 1.0D, "rare", false, "luck-multiplier", 1.0D, "health", 20.0D, "damage", 2.0D)));
            plugin.getConfig().set("client-mobs.rules." + rule + ".variants", variants);
            saveReload();
            openMobVariants(player, rule, holder(event).page);
            return;
        }
        int index = variantIndex(event.getCurrentItem());
        if (index < 0) return;
        if (event.isShiftClick() && event.isRightClick()) {
            removeVariant("client-mobs.rules", rule, index);
            openMobVariants(player, rule, holder(event).page);
        } else {
            openMobVariant(player, rule + ":" + index);
        }
    }

    private void clickMobVariant(Player player, String context, InventoryClickEvent event) {
        ConfigurationSection s = variantSection("client-mobs.rules", context);
        switch (event.getRawSlot()) {
            case 10 -> {
                openSelector(player, View.SELECT_MYTHIC, "mobvar:" + context, 0, "Choose Variant MythicMob", hooks.mythicMobIds(), value -> button("mythic", Material.ZOMBIE_HEAD, "<aqua>" + value, List.of("<yellow>Click to select")));
                return;
            }
            case 12 -> {
                openSelector(player, View.SELECT_ENTITY, "mobvar:" + context, 0, "Choose Variant Fallback", entityChoices, value -> button("entity", fallbackMaterial(value.name()), "<aqua>" + value.name(), List.of("<yellow>Click to select")));
                return;
            }
            case 14 -> setVariantValue("client-mobs.rules", context, "weight", Math.max(0.0D, s.getDouble("weight", 1.0D) + (event.isRightClick() ? 1.0D : -1.0D)));
            case 16 -> setVariantValue("client-mobs.rules", context, "rare", !s.getBoolean("rare", false));
            case 28 -> setVariantValue("client-mobs.rules", context, "luck-multiplier", Math.max(0.0D, s.getDouble("luck-multiplier", 1.0D) + (event.isRightClick() ? 0.1D : -0.1D)));
            case 30 -> setVariantValue("client-mobs.rules", context, "health", Math.max(1.0D, s.getDouble("health", 20.0D) + (event.isRightClick() ? 5.0D : -5.0D)));
            case 32 -> setVariantValue("client-mobs.rules", context, "damage", Math.max(0.0D, s.getDouble("damage", 2.0D) + (event.isRightClick() ? 1.0D : -1.0D)));
            case 34 -> cycleRequiredConditionId("client-mobs.rules", context, "mob:" + context.split(":", 2)[0]);
            default -> {
                return;
            }
        }
        saveReload();
        openMobVariant(player, context);
    }

    private void clickMobRules(Player player, InventoryClickEvent event) {
        if (event.getRawSlot() == 4) {
            String id = nextId("client-mobs.rules", "mob_rule");
            ConfigurationSection section = plugin.getConfig().createSection("client-mobs.rules." + id);
            section.set("enabled", true);
            section.set("mythicmob-id", "ZombieA");
            section.set("fallback-entity", "ZOMBIE");
            section.set("condition", "");
            section.set("priority", 0);
            section.set("health", 20.0);
            section.set("damage", 2.0);
            saveReload();
            openMobRule(player, id);
            return;
        }
        String id = itemName(event.getCurrentItem());
        if (id.isBlank()) return;
        if (event.isShiftClick() && event.isRightClick()) {
            plugin.getConfig().set("client-mobs.rules." + id, null);
            saveReload();
            openMobRules(player, holder(event).page);
        } else if (event.isShiftClick()) {
            toggleConfig("client-mobs.rules." + id + ".enabled");
            saveReload();
            openMobRules(player, holder(event).page);
        } else {
            openMobRule(player, id);
        }
    }

    private void clickMobRule(Player player, String id, InventoryClickEvent event) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("client-mobs.rules." + id);
        if (s == null) return;
        switch (event.getRawSlot()) {
            case 10 -> {
                toggleConfig("client-mobs.rules." + id + ".enabled");
                saveReload();
                openMobRule(player, id);
            }
            case 12 -> openSelector(player, View.SELECT_MYTHIC, id, 0, "Choose MythicMob", hooks.mythicMobIds(), value -> button("mythic", Material.ZOMBIE_HEAD, "<aqua>" + value, List.of("<yellow>Click to select")));
            case 14 -> openSelector(player, View.SELECT_ENTITY, id, 0, "Choose Fallback Entity", entityChoices, value -> button("entity", fallbackMaterial(value.name()), "<aqua>" + value.name(), List.of("<yellow>Click to select")));
            case 16 -> {
                s.set("health", Math.max(1.0, s.getDouble("health", 20.0) + (event.isRightClick() ? 5.0 : -5.0)));
                saveReload();
                openMobRule(player, id);
            }
            case 28 -> {
                s.set("damage", Math.max(0.0, s.getDouble("damage", 2.0) + (event.isRightClick() ? 1.0 : -1.0)));
                saveReload();
                openMobRule(player, id);
            }
            case 30 -> openConditions(player, "mob:" + id, 0);
            case 32 -> openMobVariants(player, id, 0);
            default -> {
            }
        }
    }

    private void clickSpawns(Player player, InventoryClickEvent event) {
        if (event.getRawSlot() == 4) {
            String id = nextSpawnId();
            mobs.setSpawn(id, player.getLocation());
            openSpawn(player, id);
            return;
        }
        String id = itemName(event.getCurrentItem());
        if (id.isBlank()) return;
        if (event.isShiftClick() && event.isRightClick()) {
            mobs.deleteSpawn(id);
            openSpawns(player, holder(event).page);
        } else {
            openSpawn(player, id);
        }
    }

    private void clickSpawn(Player player, String id, InventoryClickEvent event) {
        SpawnPoint p = mobs.spawn(id).orElse(null);
        if (p == null) return;
        switch (event.getRawSlot()) {
            case 10 -> p.set("enabled", String.valueOf(!p.enabled()));
            case 12 -> {
                openSelector(player, View.SELECT_MOB_RULE, id, 0, "Choose Spawn Rule", mobs.ruleIds(), value -> button("rule", Material.NAME_TAG, "<aqua>" + value, List.of("<yellow>Click to select")));
                return;
            }
            case 14 -> p.set("amount", String.valueOf(p.amount() + (event.isRightClick() ? 1 : -1)));
            case 16 -> p.set("batch-size", String.valueOf(p.batchSize() + (event.isRightClick() ? 1 : -1)));
            case 28 -> p.set("radius", String.valueOf(p.radius() + (event.isRightClick() ? 1 : -1)));
            case 30 -> p.set("interval-ticks", String.valueOf(p.intervalTicks() + (event.isRightClick() ? 20 : -20)));
            case 32 -> p.set("max-alive", String.valueOf(p.maxAlive() + (event.isRightClick() ? 1 : -1)));
            case 34 -> p.set("activation-range", String.valueOf(p.activationRange() + (event.isRightClick() ? 4 : -4)));
            default -> {
                return;
            }
        }
        mobs.saveSpawns();
        openSpawn(player, id);
    }

    private void clickDrops(Player player, String ruleId, InventoryClickEvent event) {
        if (event.getRawSlot() == 4) {
            addDrop(ruleId);
            openDrops(player, ruleId, 0);
            return;
        }
        String name = itemName(event.getCurrentItem());
        if (!name.startsWith("Drop #")) return;
        int index = Integer.parseInt(name.substring(6)) - 1;
        if (event.isShiftClick() && event.isRightClick()) {
            removeDrop(ruleId, index);
            openDrops(player, ruleId, 0);
        } else {
            openDrop(player, ruleId + ":" + index);
        }
    }

    private void clickDrop(Player player, String context, InventoryClickEvent event) {
        ConfigurationSection drop = dropSection(context);
        switch (event.getRawSlot()) {
            case 10 -> setDropValue(context, "type", drop.getString("type", "vanilla").equalsIgnoreCase("mmoitems") ? "vanilla" : "mmoitems");
            case 12 -> {
                openSelector(player, View.SELECT_ITEM, "dropmat:" + context, 0, "Choose Item Material", itemChoices, this::itemMaterial);
                return;
            }
            case 14 -> setDropValue(context, "amount", Math.max(1, drop.getInt("amount", 1) + (event.isRightClick() ? 1 : -1)));
            case 19 -> setDropValue(context, "unbreakable", !drop.getBoolean("unbreakable", false));
            case 20 -> setDropValue(context, "hide-tooltip", !drop.getBoolean("hide-tooltip", false));
            case 21 -> cycleGlint(context, drop);
            case 22 -> setDropValue(context, "glider", !drop.getBoolean("glider", false));
            case 23 -> setDropValue(context, "fire-resistant", !drop.getBoolean("fire-resistant", false));
            case 24 -> numberDropValue(context, drop, "custom-model-data", event, 0, 999999);
            case 25 -> numberDropValue(context, drop, "max-stack-size", event, 1, 99);
            case 28 -> cycleRarity(context, drop);
            case 29 -> numberDropValue(context, drop, "enchantable", event, 1, 999);
            case 30 -> {
                openSelector(player, View.SELECT_MMO_TYPE, context, 0, "Choose MMOItems Type", hooks.mmoItemTypes(), value -> button("mmo-type", Material.BOOK, "<aqua>" + value, List.of("<yellow>Click to select")));
                return;
            }
            case 32 -> {
                String type = drop.getString("mmo-type", "");
                openSelector(player, View.SELECT_MMO_ITEM, context, 0, "Choose MMOItems Item", hooks.mmoItemIds(type), value -> button("mmo-item", Material.ENCHANTED_BOOK, "<aqua>" + value, List.of("<yellow>Click to select")));
                return;
            }
            default -> {
                return;
            }
        }
        saveReload();
        openDrop(player, context);
    }

    private void selectBlock(Player player, String context, InventoryClickEvent event) {
        String selected = itemName(event.getCurrentItem());
        Material material = Material.matchMaterial(selected);
        if (material == null) return;
        String[] split = context.split(":", 2);
        String rule = split[1];
        if (split[0].equals("display")) {
            plugin.getConfig().set("block-regen.rules." + rule + ".display-block", material.name());
        } else if (split[0].equals("blockvar")) {
            setVariantValue("block-regen.rules", rule, "display-block", material.name());
        } else {
            List<String> blocks = new ArrayList<>(plugin.getConfig().getStringList("block-regen.rules." + rule + ".source-blocks"));
            if (!blocks.contains(material.name())) blocks.add(material.name());
            plugin.getConfig().set("block-regen.rules." + rule + ".source-blocks", blocks);
        }
        saveReload();
        if (split[0].equals("blockvar")) {
            openBlockVariant(player, rule);
        } else {
            openBlockRule(player, rule);
        }
    }

    private void selectItem(Player player, String context, InventoryClickEvent event) {
        Material material = materialFrom(event.getCurrentItem());
        if (material == null) return;
        if (context.equals("browser")) {
            openVanillaDropBrowser(player, holder(event).page);
            return;
        }
        String dropContext = context.substring("dropmat:".length());
        setDropValue(dropContext, "material", material.name());
        saveReload();
        openDrop(player, dropContext);
    }

    private void selectMythic(Player player, String rule, InventoryClickEvent event) {
        String mob = itemName(event.getCurrentItem());
        if (rule.startsWith("mobvar:")) {
            String context = rule.substring("mobvar:".length());
            setVariantValue("client-mobs.rules", context, "mythicmob-id", mob);
            saveReload();
            openMobVariant(player, context);
            return;
        }
        plugin.getConfig().set("client-mobs.rules." + rule + ".mythicmob-id", mob);
        saveReload();
        openMobRule(player, rule);
    }

    private void selectEntity(Player player, String rule, InventoryClickEvent event) {
        String entity = itemName(event.getCurrentItem());
        if (rule.startsWith("mobvar:")) {
            String context = rule.substring("mobvar:".length());
            setVariantValue("client-mobs.rules", context, "fallback-entity", entity);
            saveReload();
            openMobVariant(player, context);
            return;
        }
        plugin.getConfig().set("client-mobs.rules." + rule + ".fallback-entity", entity);
        saveReload();
        openMobRule(player, rule);
    }

    private void selectMobRule(Player player, String spawn, InventoryClickEvent event) {
        String rule = itemName(event.getCurrentItem());
        mobs.spawn(spawn).ifPresent(point -> {
            point.set("rule", rule);
            mobs.saveSpawns();
        });
        openSpawn(player, spawn);
    }

    private void selectMmoType(Player player, String context, InventoryClickEvent event) {
        String type = itemName(event.getCurrentItem());
        setDropValue(context, "mmo-type", type);
        saveReload();
        openDrop(player, context);
    }

    private void selectMmoItem(Player player, String context, InventoryClickEvent event) {
        String id = itemName(event.getCurrentItem());
        setDropValue(context, "mmo-id", id);
        saveReload();
        openDrop(player, context);
    }

    private void openVanillaDropBrowser(Player player, int page) {
        openSelector(player, View.SELECT_ITEM, "browser", page, "Minecraft Items", itemChoices, this::itemMaterial);
    }

    private void openPaged(Player player, View view, String id, int page) {
        switch (view) {
            case BLOCK_RULES -> openBlockRules(player, page);
            case CONDITIONS -> openConditions(player, id, page);
            case BLOCK_VARIANTS -> openBlockVariants(player, id, page);
            case MOB_VARIANTS -> openMobVariants(player, id, page);
            case MOB_RULES -> openMobRules(player, page);
            case SPAWNS -> openSpawns(player, page);
            case DROPS -> openDrops(player, id, page);
            case SOURCE_BLOCKS -> openSourceBlocks(player, id, page);
            case SELECT_BLOCK -> openSelector(player, view, id, page, "Choose Block", blockChoices, this::blockItem);
            case SELECT_ITEM -> openSelector(player, view, id, page, "Choose Item", itemChoices, this::itemMaterial);
            case SELECT_MYTHIC -> openSelector(player, view, id, page, "Choose MythicMob", hooks.mythicMobIds(), value -> button("mythic", Material.ZOMBIE_HEAD, "<aqua>" + value, List.of("<yellow>Click to select")));
            case SELECT_ENTITY -> openSelector(player, view, id, page, "Choose Entity", entityChoices, value -> button("entity", fallbackMaterial(value.name()), "<aqua>" + value.name(), List.of("<yellow>Click to select")));
            case SELECT_MOB_RULE -> openSelector(player, view, id, page, "Choose Spawn Rule", mobs.ruleIds(), value -> button("rule", Material.NAME_TAG, "<aqua>" + value, List.of("<yellow>Click to select")));
            case SELECT_MMO_TYPE -> openSelector(player, view, id, page, "Choose MMOItems Type", hooks.mmoItemTypes(), value -> button("mmo-type", Material.BOOK, "<aqua>" + value, List.of("<yellow>Click to select")));
            case SELECT_MMO_ITEM -> openSelector(player, view, id, page, "Choose MMOItems Item", hooks.mmoItemIds(dropSection(id).getString("mmo-type", "")), value -> button("mmo-item", Material.ENCHANTED_BOOK, "<aqua>" + value, List.of("<yellow>Click to select")));
            default -> openMain(player);
        }
    }

    private void openBack(Player player, Holder h) {
        switch (h.view) {
            case BLOCK_RULES, MOB_RULES, SPAWNS -> openMain(player);
            case BLOCK_RULE -> openBlockRules(player, 0);
            case CONDITIONS -> {
                String[] split = h.id.split(":", 2);
                if (split[0].equals("block")) openBlockRule(player, split[1]);
                else openMobRule(player, split[1]);
            }
            case BLOCK_VARIANTS -> openBlockRule(player, h.id);
            case BLOCK_VARIANT -> openBlockVariants(player, h.id.split(":", 2)[0], 0);
            case MOB_RULE -> openMobRules(player, 0);
            case MOB_VARIANTS -> openMobRule(player, h.id);
            case MOB_VARIANT -> openMobVariants(player, h.id.split(":", 2)[0], 0);
            case SPAWN_DETAIL -> openSpawns(player, 0);
            case DROPS, SOURCE_BLOCKS -> openBlockRule(player, h.id);
            case DROP_DETAIL -> openDrops(player, h.id.split(":", 2)[0], 0);
            default -> openMain(player);
        }
    }

    private Inventory menu(View view, String id, int page, String title) {
        Inventory inv = Bukkit.createInventory(new Holder(view, id, page), 54, Text.mm("<dark_gray>" + title));
        fill(inv);
        return inv;
    }

    private Inventory pageMenu(View view, String id, int page, String title) {
        Inventory inv = menu(view, id, page, title + " <gray>(" + (page + 1) + ")");
        inv.setItem(45, button("nav.prev", Material.ARROW, "<yellow>Previous Page", List.of()));
        inv.setItem(49, back());
        inv.setItem(53, button("nav.next", Material.ARROW, "<yellow>Next Page", List.of()));
        return inv;
    }

    private <T> void paginate(Inventory inv, List<T> values, int page, java.util.function.Function<T, ItemStack> renderer) {
        int start = Math.max(0, page) * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length && start + i < values.size(); i++) {
            inv.setItem(CONTENT_SLOTS[i], renderer.apply(values.get(start + i)));
        }
    }

    private List<BlockChoice> buildBlockChoices() {
        return Registry.BLOCK.keyStream()
                .map(key -> key.getKey().toUpperCase(Locale.ROOT))
                .filter(name -> !name.endsWith("AIR"))
                .map(name -> new BlockChoice(name, safeIcon(Material.matchMaterial(name), Material.PAPER)))
                .sorted(Comparator.comparing(BlockChoice::id))
                .toList();
    }

    private List<Material> buildItemChoices() {
        return List.of(Material.values()).stream()
                .filter(material -> !material.name().startsWith("LEGACY_"))
                .filter(material -> !material.name().endsWith("AIR"))
                .filter(material -> safeIcon(material, null) != null)
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private List<EntityType> buildEntityChoices() {
        return List.of(EntityType.values()).stream().filter(EntityType::isAlive).sorted(Comparator.comparing(Enum::name)).toList();
    }

    private ItemStack blockItem(BlockChoice choice) {
        return button("block", choice.icon(), "<aqua>" + choice.id(), List.of("<gray>Icon: <white>" + choice.icon().name(), "<yellow>Click to select"));
    }

    private ItemStack itemMaterial(Material material) {
        return button("item", material, "<aqua>" + material.name(), List.of("<yellow>Click to select"));
    }

    private void addDrop(String ruleId) {
        List<Map<?, ?>> drops = new ArrayList<>(plugin.getConfig().getMapList("block-regen.rules." + ruleId + ".drops"));
        drops.add(Map.of("type", "vanilla", "material", "STONE", "amount", 1));
        plugin.getConfig().set("block-regen.rules." + ruleId + ".drops", drops);
        saveReload();
    }

    private void removeDrop(String ruleId, int index) {
        List<Map<?, ?>> drops = new ArrayList<>(plugin.getConfig().getMapList("block-regen.rules." + ruleId + ".drops"));
        if (index >= 0 && index < drops.size()) {
            drops.remove(index);
            plugin.getConfig().set("block-regen.rules." + ruleId + ".drops", drops);
            saveReload();
        }
    }

    private ConfigurationSection dropSection(String context) {
        String[] split = context.split(":", 2);
        return dropSection(split[0], Integer.parseInt(split[1]));
    }

    private ConfigurationSection dropSection(String ruleId, int index) {
        List<Map<?, ?>> drops = plugin.getConfig().getMapList("block-regen.rules." + ruleId + ".drops");
        return tempSection(drops.get(index));
    }

    private ConfigurationSection tempSection(Map<?, ?> map) {
        YamlConfiguration temp = new YamlConfiguration();
        return temp.createSection("data", map);
    }

    private ConfigurationSection variantSection(String root, String context) {
        String[] split = context.split(":", 2);
        List<Map<?, ?>> variants = plugin.getConfig().getMapList(root + "." + split[0] + ".variants");
        return tempSection(variants.get(Integer.parseInt(split[1])));
    }

    private void setVariantValue(String root, String context, String key, Object value) {
        String[] split = context.split(":", 2);
        String ruleId = split[0];
        int index = Integer.parseInt(split[1]);
        List<Map<?, ?>> raw = new ArrayList<>(plugin.getConfig().getMapList(root + "." + ruleId + ".variants"));
        if (index < 0 || index >= raw.size()) return;
        Map<String, Object> variant = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.get(index).entrySet()) {
            variant.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        variant.put(key, value);
        raw.set(index, variant);
        plugin.getConfig().set(root + "." + ruleId + ".variants", raw);
    }

    private void removeVariant(String root, String ruleId, int index) {
        List<Map<?, ?>> raw = new ArrayList<>(plugin.getConfig().getMapList(root + "." + ruleId + ".variants"));
        if (index >= 0 && index < raw.size()) {
            raw.remove(index);
            plugin.getConfig().set(root + "." + ruleId + ".variants", raw);
            saveReload();
        }
    }

    private void cycleRequiredConditionId(String root, String variantContext, String conditionContext) {
        ConfigurationSection current = variantSection(root, variantContext);
        List<String> selected = new ArrayList<>(current.getStringList("required-condition-ids"));
        List<String> available = optionalConditionIds(plugin.getConfig().getStringList(conditionPath(conditionContext)));
        if (available.isEmpty()) {
            setVariantValue(root, variantContext, "required-condition-ids", List.of());
            return;
        }
        String next = available.stream().filter(id -> !selected.contains(id)).findFirst().orElse(null);
        if (next == null) {
            selected.clear();
        } else {
            selected.add(next);
        }
        setVariantValue(root, variantContext, "required-condition-ids", selected);
    }

    private List<String> optionalConditionIds(List<String> lines) {
        List<String> ids = new ArrayList<>();
        for (String line : lines) {
            String[] split = line.split(";", 5);
            if (split.length == 5 && split[4].equalsIgnoreCase("optional") && !split[0].isBlank()) {
                ids.add(split[0]);
            }
        }
        return ids;
    }

    private String conditionPath(String context) {
        String[] split = context.split(":", 2);
        return (split[0].equals("block") ? "block-regen.rules." : "client-mobs.rules.") + split[1] + ".conditions";
    }

    private int variantIndex(ItemStack item) {
        String name = itemName(item);
        if (!name.startsWith("Variant #")) return -1;
        try {
            return Integer.parseInt(name.substring("Variant #".length())) - 1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String toggleOptional(String line) {
        if (line.endsWith(";optional")) {
            return line.substring(0, line.length() - ";optional".length());
        }
        return line + ";optional";
    }

    private String cycleConditionOperator(String line) {
        String[] split = line.split(";", 5);
        if (split.length < 4) return line;
        int operatorIndex = split.length >= 5 ? 2 : 1;
        split[operatorIndex] = switch (split[operatorIndex]) {
            case ">=" -> "<=";
            case "<=" -> "==";
            case "==" -> "!=";
            case "!=" -> ">";
            case ">" -> "<";
            default -> ">=";
        };
        return String.join(";", split);
    }

    private void setDropValue(String context, String key, Object value) {
        String[] split = context.split(":", 2);
        String ruleId = split[0];
        int index = Integer.parseInt(split[1]);
        List<Map<?, ?>> rawDrops = new ArrayList<>(plugin.getConfig().getMapList("block-regen.rules." + ruleId + ".drops"));
        if (index < 0 || index >= rawDrops.size()) {
            return;
        }
        Map<String, Object> drop = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawDrops.get(index).entrySet()) {
            drop.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        drop.put(key, value);
        rawDrops.set(index, drop);
        plugin.getConfig().set("block-regen.rules." + ruleId + ".drops", rawDrops);
    }

    private void removeDropValue(String context, String key) {
        String[] split = context.split(":", 2);
        String ruleId = split[0];
        int index = Integer.parseInt(split[1]);
        List<Map<?, ?>> rawDrops = new ArrayList<>(plugin.getConfig().getMapList("block-regen.rules." + ruleId + ".drops"));
        if (index < 0 || index >= rawDrops.size()) {
            return;
        }
        Map<String, Object> drop = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawDrops.get(index).entrySet()) {
            drop.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        drop.remove(key);
        rawDrops.set(index, drop);
        plugin.getConfig().set("block-regen.rules." + ruleId + ".drops", rawDrops);
    }

    private void numberDropValue(String context, ConfigurationSection drop, String key, InventoryClickEvent event, int min, int max) {
        if (event.isShiftClick() && event.isRightClick()) {
            removeDropValue(context, key);
            return;
        }
        int current = drop.getInt(key, key.equals("max-stack-size") || key.equals("enchantable") ? min : 0);
        int delta = event.isRightClick() ? 1 : -1;
        setDropValue(context, key, Math.max(min, Math.min(max, current + delta)));
    }

    private void cycleGlint(String context, ConfigurationSection drop) {
        if (!drop.contains("enchantment-glint")) {
            setDropValue(context, "enchantment-glint", true);
        } else if (drop.getBoolean("enchantment-glint", false)) {
            setDropValue(context, "enchantment-glint", false);
        } else {
            removeDropValue(context, "enchantment-glint");
        }
    }

    private void cycleRarity(String context, ConfigurationSection drop) {
        String next = switch (drop.getString("rarity", "COMMON").toUpperCase(Locale.ROOT)) {
            case "COMMON" -> "UNCOMMON";
            case "UNCOMMON" -> "RARE";
            case "RARE" -> "EPIC";
            default -> "COMMON";
        };
        setDropValue(context, "rarity", next);
    }

    private List<String> configKeys(String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        return section == null ? List.of() : section.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void toggleConfig(String path) {
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, true));
    }

    private String nextId(String sectionPath, String prefix) {
        int index = 1;
        while (plugin.getConfig().contains(sectionPath + "." + prefix + "_" + index)) {
            index++;
        }
        return prefix + "_" + index;
    }

    private String nextSpawnId() {
        int index = 1;
        while (mobs.spawn("spawn_" + index).isPresent()) {
            index++;
        }
        return "spawn_" + index;
    }

    private void saveReload() {
        plugin.saveConfig();
        plugin.reloadPlugin();
    }

    private String title(String key, String fallback) {
        return plugin.getConfig().getString("gui.titles." + key, fallback);
    }

    private ItemStack button(String key, Material fallbackMaterial, String fallbackName, List<String> fallbackLore) {
        Material material = safeIcon(Material.matchMaterial(plugin.getConfig().getString("gui.icons." + key + ".material", fallbackMaterial.name())), fallbackMaterial);
        String name = plugin.getConfig().getString("gui.icons." + key + ".name", fallbackName);
        List<String> lore = plugin.getConfig().contains("gui.icons." + key + ".lore") ? plugin.getConfig().getStringList("gui.icons." + key + ".lore") : fallbackLore;
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        stack.setItemMeta(meta);
        return stack;
    }

    private void fill(Inventory inv) {
        ItemStack filler = button("filler", Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private ItemStack back() {
        return button("nav.back", Material.BARRIER, "<yellow>Back", List.of("<gray>Return to previous menu."));
    }

    private static Material materialFrom(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        String name = itemName(item);
        Material material = Material.matchMaterial(name);
        return material == null ? item.getType() : material;
    }

    private static String itemName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return "";
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).trim();
    }

    private static Material displayMaterial(String material) {
        Material parsed = Material.matchMaterial(material == null ? "STONE" : material);
        return safeIcon(parsed, Material.STONE);
    }

    private static Material safeIcon(Material material, Material fallback) {
        if (material == null || material.name().startsWith("LEGACY_") || material.name().endsWith("AIR")) {
            return fallback;
        }
        try {
            ItemStack.of(material);
            return material;
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static Material fallbackMaterial(String entity) {
        return switch (entity.toUpperCase(Locale.ROOT)) {
            case "SKELETON" -> Material.SKELETON_SKULL;
            case "CREEPER" -> Material.CREEPER_HEAD;
            case "PIGLIN", "PIGLIN_BRUTE" -> Material.PIGLIN_HEAD;
            default -> Material.ZOMBIE_HEAD;
        };
    }

    private static Material glintIcon(ConfigurationSection section) {
        if (!section.contains("enchantment-glint")) {
            return Material.GRAY_DYE;
        }
        return section.getBoolean("enchantment-glint", false) ? Material.ENCHANTED_BOOK : Material.BOOK;
    }

    private static Material rarityIcon(String rarity) {
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> Material.GREEN_DYE;
            case "RARE" -> Material.BLUE_DYE;
            case "EPIC" -> Material.PURPLE_DYE;
            default -> Material.WHITE_DYE;
        };
    }

    private static List<String> spawnLore(SpawnPoint p) {
        return List.of(
                "<gray>Rule: <white>" + blank(p.rule()),
                "<gray>Amount: <white>" + p.amount() + " <gray>Batch: <white>" + p.batchSize(),
                "<gray>Radius: <white>" + p.radius() + " <gray>Interval: <white>" + p.intervalTicks(),
                "<gray>Max alive: <white>" + p.maxAlive() + " <gray>Range: <white>" + p.activationRange(),
                "<yellow>Click to edit"
        );
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "auto" : value;
    }

    private static Holder holder(InventoryClickEvent event) {
        return (Holder) event.getInventory().getHolder();
    }

    private enum View {
        MAIN,
        BLOCK_RULES,
        BLOCK_RULE,
        SOURCE_BLOCKS,
        CONDITIONS,
        BLOCK_VARIANTS,
        BLOCK_VARIANT,
        MOB_RULES,
        MOB_RULE,
        MOB_VARIANTS,
        MOB_VARIANT,
        SPAWNS,
        SPAWN_DETAIL,
        DROPS,
        DROP_DETAIL,
        SELECT_BLOCK,
        SELECT_ITEM,
        SELECT_MYTHIC,
        SELECT_ENTITY,
        SELECT_MOB_RULE,
        SELECT_MMO_TYPE,
        SELECT_MMO_ITEM
    }

    private record Holder(View view, String id, int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BlockChoice(String id, Material icon) {
    }
}

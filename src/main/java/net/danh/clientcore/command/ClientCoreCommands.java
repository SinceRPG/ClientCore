package net.danh.clientcore.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.clientcore.ClientCore;
import net.danh.clientcore.block.BlockRegenService;
import net.danh.clientcore.build.ClientBuildService;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.hook.HookRegistry;
import net.danh.clientcore.luck.LuckItemService;
import net.danh.clientcore.luck.LuckService;
import net.danh.clientcore.mob.ClientMobService;
import net.danh.clientcore.mob.SpawnPoint;
import net.danh.clientcore.storage.PlayerStats;
import net.danh.clientcore.util.Text;
import net.danh.clientcore.visibility.VisibilityService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClientCoreCommands {
    private ClientCoreCommands() {
    }

    public static void register(ClientCore plugin, ConfigManager config, HookRegistry hooks, BlockRegenService blockService, ClientMobService mobService, ClientBuildService buildService, VisibilityService visibility, LuckService luck, LuckItemService luckItems) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, (ReloadableRegistrarEvent<Commands> event) -> {

            LiteralArgumentBuilder<CommandSourceStack> rootBuilder = Commands.literal("clientcore");

            rootBuilder.then(Commands.literal("reload")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .executes(context -> {
                        plugin.reloadPlugin();
                        Text.sendConfig(context.getSource().getSender(), config, "commands.reloaded");
                        return 1;
                    }));

            rootBuilder.then(Commands.literal("status")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .executes(context -> {
                        CommandSender sender = context.getSource().getSender();
                        Text.sendConfig(sender, config, "commands.status",
                                "{blocks}", String.valueOf(blockService.ruleCount()),
                                "{vanilla_mining}", String.valueOf(blockService.vanillaMiningRuleCount()),
                                "{farming}", String.valueOf(blockService.farmingRuleCount()),
                                "{mobs}", String.valueOf(mobService.ruleCount()),
                                "{oraxen}", hookState(hooks.hasOraxen()),
                                "{itemsadder}", hookState(hooks.hasItemsAdder()),
                                "{nexo}", hookState(hooks.hasNexo()),
                                "{craftengine}", hookState(hooks.hasCraftEngine()));
                        return 1;
                    }));

            rootBuilder.then(Commands.literal("refresh")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .executes(context -> {
                        if (!(context.getSource().getSender() instanceof Player player)) {
                            Text.sendConfig(context.getSource().getSender(), config, "commands.only-players");
                            return 0;
                        }
                        blockService.refreshAround(player);
                        Text.sendConfig(player, config, "commands.blocks-refreshed");
                        return 1;
                    }));

            rootBuilder.then(Commands.literal("visibility")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (String value : List.of("toggle", "hide", "show")) {
                                    builder.suggest(value);
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                if (!(context.getSource().getSender() instanceof Player player)) {
                                    Text.sendConfig(context.getSource().getSender(), config, "commands.only-players");
                                    return 0;
                                }
                                String mode = StringArgumentType.getString(context, "mode");
                                boolean hidden;
                                if (mode.equalsIgnoreCase("hide")) {
                                    visibility.hide(player);
                                    hidden = true;
                                } else if (mode.equalsIgnoreCase("show")) {
                                    visibility.show(player);
                                    hidden = false;
                                } else {
                                    hidden = visibility.toggle(player);
                                }
                                String path = hidden ? "commands.visibility-hidden" : "commands.visibility-visible";
                                Text.sendConfig(player, config, path);
                                return 1;
                            })));

            rootBuilder.then(Commands.literal("mobspawn")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 50))
                            .executes(context -> {
                                if (!(context.getSource().getSender() instanceof Player player)) {
                                    Text.sendConfig(context.getSource().getSender(), config, "commands.only-players");
                                    return 0;
                                }
                                int amount = IntegerArgumentType.getInteger(context, "amount");
                                plugin.scheduler().entity(player, () -> {
                                    for (int i = 0; i < amount; i++) {
                                        Location location = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(3 + i));
                                        plugin.scheduler().region(location, () -> mobService.spawnFor(player, location));
                                    }
                                });
                                Text.sendConfig(player, config, "commands.mob-spawned", "{amount}", String.valueOf(amount));
                                return amount;
                            })));

            rootBuilder.then(Commands.literal("mythicspawn")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("viewer", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                if (context.getSource().getSender() instanceof Player) {
                                    builder.suggest("self");
                                }
                                return suggestPlayers(builder);
                            })
                            .then(Commands.argument("mob", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        for (String value : mobService.mythicMobIds()) {
                                            builder.suggest(value);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> spawnMythic(plugin, config, mobService, context.getSource().getSender(),
                                            StringArgumentType.getString(context, "viewer"),
                                            StringArgumentType.getString(context, "mob"), 1.0D, 1))
                                    .then(Commands.argument("level", DoubleArgumentType.doubleArg(0.0D))
                                            .suggests((context, builder) -> {
                                                for (String value : List.of("1", "5", "10", "25", "50", "100")) {
                                                    builder.suggest(value);
                                                }
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> spawnMythic(plugin, config, mobService, context.getSource().getSender(),
                                                    StringArgumentType.getString(context, "viewer"),
                                                    StringArgumentType.getString(context, "mob"),
                                                    DoubleArgumentType.getDouble(context, "level"), 1))
                                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 50))
                                                    .suggests((context, builder) -> {
                                                        for (String value : List.of("1", "2", "3", "5", "10")) {
                                                            builder.suggest(value);
                                                        }
                                                        return builder.buildFuture();
                                                    })
                                                    .executes(context -> spawnMythic(plugin, config, mobService, context.getSource().getSender(),
                                                            StringArgumentType.getString(context, "viewer"),
                                                            StringArgumentType.getString(context, "mob"),
                                                            DoubleArgumentType.getDouble(context, "level"),
                                                            IntegerArgumentType.getInteger(context, "amount"))))))));

            rootBuilder.then(Commands.literal("setspawn")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("id", StringArgumentType.word())
                            .executes(context -> {
                                if (!(context.getSource().getSender() instanceof Player player)) {
                                    Text.sendConfig(context.getSource().getSender(), config, "commands.only-players");
                                    return 0;
                                }
                                String id = StringArgumentType.getString(context, "id");
                                plugin.scheduler().entity(player, () -> {
                                    mobService.setSpawn(id, player.getLocation());
                                    Text.sendConfig(player, config, "commands.spawn-set", "{id}", id);
                                });
                                return 1;
                            })));

            rootBuilder.then(Commands.literal("delspawn")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("id", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (String value : mobService.spawnIds()) {
                                    builder.suggest(value);
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                String id = StringArgumentType.getString(context, "id");
                                boolean removed = mobService.deleteSpawn(id);
                                String path = removed ? "commands.spawn-deleted" : "commands.spawn-not-found";
                                Text.sendConfig(context.getSource().getSender(), config, path, "{id}", id);
                                return removed ? 1 : 0;
                            })));

            rootBuilder.then(Commands.literal("spawns")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .executes(context -> {
                        CommandSender sender = context.getSource().getSender();
                        if (mobService.spawnList().isEmpty()) {
                            Text.sendConfig(sender, config, "commands.spawn-none");
                            return 1;
                        }
                        for (SpawnPoint point : mobService.spawnList()) {
                            Text.sendConfig(sender, config, "commands.spawn-list-item",
                                    "{id}", point.id(), "{rule}", blank(point.rule()),
                                    "{amount}", String.valueOf(point.amount()), "{radius}", String.valueOf(point.radius()));
                        }
                        return mobService.spawnList().size();
                    }));

            rootBuilder.then(Commands.literal("spawnattr")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("id", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (String value : mobService.spawnIds()) {
                                    builder.suggest(value);
                                }
                                return builder.buildFuture();
                            })
                            .then(Commands.argument("attribute", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        for (String value : List.of("rule", "amount", "radius", "batch-size", "interval-ticks", "max-alive", "level", "activation-range", "enabled")) {
                                            builder.suggest(value);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .then(Commands.argument("value", StringArgumentType.greedyString())
                                            .suggests((context, builder) -> {
                                                String attribute = StringArgumentType.getString(context, "attribute");
                                                if (attribute.equalsIgnoreCase("rule")) {
                                                    for (String value : mobService.ruleIds()) {
                                                        builder.suggest(value);
                                                    }
                                                } else if (attribute.equalsIgnoreCase("enabled")) {
                                                    builder.suggest("true");
                                                    builder.suggest("false");
                                                }
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "id");
                                                String attribute = StringArgumentType.getString(context, "attribute");
                                                String value = StringArgumentType.getString(context, "value");
                                                SpawnPoint point = mobService.spawn(id).orElse(null);
                                                if (point == null) {
                                                    Text.sendConfig(context.getSource().getSender(), config, "commands.spawn-not-found", "{id}", id);
                                                    return 0;
                                                }
                                                try {
                                                    point.set(attribute, value);
                                                    mobService.saveSpawns();
                                                    Text.sendConfig(context.getSource().getSender(), config, "commands.spawn-updated",
                                                            "{id}", id, "{attribute}", attribute, "{value}", value);
                                                    return 1;
                                                } catch (IllegalArgumentException ex) {
                                                    Text.sendConfig(context.getSource().getSender(), config, "commands.spawn-invalid-attribute");
                                                    return 0;
                                                }
                                            })))));

            rootBuilder.then(Commands.literal("debug")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("key", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (String value : List.of("on", "off")) {
                                    builder.suggest(value);
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                String key = StringArgumentType.getString(context, "key");
                                boolean enabled = key.equalsIgnoreCase("on") || key.equalsIgnoreCase("true");
                                config.getMain().set("settings.debug", enabled);
                                config.saveAll();
                                Text.sendConfig(context.getSource().getSender(), config, "commands.debug-set", "{state}", String.valueOf(enabled));
                                return 1;
                            })));

            rootBuilder.then(buildCommandTree(plugin, config, buildService));

            LiteralArgumentBuilder<CommandSourceStack> luckCmd = Commands.literal("luck");

            luckCmd.then(Commands.literal("profile")
                    .requires(source -> source.getSender().hasPermission("clientcore.luck"))
                    .executes(context -> {
                        if (!(context.getSource().getSender() instanceof Player player)) {
                            Text.sendConfig(context.getSource().getSender(), config, "commands.only-players");
                            return 0;
                        }
                        PlayerStats stats = luck.snapshot(player);
                        Text.sendConfig(player, config, "commands.luck-profile", "{luck}", String.valueOf(stats.luck()));
                        return 1;
                    }));

            luckCmd.then(Commands.literal("top")
                    .requires(source -> source.getSender().hasPermission("clientcore.luck"))
                    .executes(context -> {
                        sendLuckTop(plugin, config, context.getSource().getSender(), luck, 10);
                        return 1;
                    }));

            luckCmd.then(Commands.literal("set")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((context, builder) -> suggestPlayers(builder))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes(context -> {
                                        PlayerTarget target = target(StringArgumentType.getString(context, "player"));
                                        PlayerStats stats = luck.set(target.uuid(), target.name(), IntegerArgumentType.getInteger(context, "amount"));
                                        Text.sendConfig(context.getSource().getSender(), config, "commands.luck-set", "{player}", stats.name(), "{luck}", String.valueOf(stats.luck()));
                                        return 1;
                                    }))));

            luckCmd.then(Commands.literal("add")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((context, builder) -> suggestPlayers(builder))
                            .then(Commands.argument("amount", IntegerArgumentType.integer())
                                    .executes(context -> {
                                        PlayerTarget target = target(StringArgumentType.getString(context, "player"));
                                        PlayerStats stats = luck.add(target.uuid(), target.name(), IntegerArgumentType.getInteger(context, "amount"));
                                        Text.sendConfig(context.getSource().getSender(), config, "commands.luck-added", "{player}", stats.name(), "{luck}", String.valueOf(stats.luck()));
                                        return 1;
                                    }))));

            luckCmd.then(Commands.literal("remove")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((context, builder) -> suggestPlayers(builder))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes(context -> {
                                        PlayerTarget target = target(StringArgumentType.getString(context, "player"));
                                        PlayerStats stats = luck.add(target.uuid(), target.name(), -IntegerArgumentType.getInteger(context, "amount"));
                                        Text.sendConfig(context.getSource().getSender(), config, "commands.luck-removed", "{player}", stats.name(), "{luck}", String.valueOf(stats.luck()));
                                        return 1;
                                    }))));

            luckCmd.then(Commands.literal("reset")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((context, builder) -> suggestPlayers(builder))
                            .executes(context -> {
                                PlayerTarget target = target(StringArgumentType.getString(context, "player"));
                                PlayerStats stats = luck.set(target.uuid(), target.name(), 0.0D);
                                Text.sendConfig(context.getSource().getSender(), config, "commands.luck-reset", "{player}", stats.name());
                                return 1;
                            })));

            luckCmd.then(Commands.literal("top-exclude")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((context, builder) -> suggestPlayers(builder))
                            .then(Commands.argument("value", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        builder.suggest("true");
                                        builder.suggest("false");
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> {
                                        PlayerTarget target = target(StringArgumentType.getString(context, "player"));
                                        boolean excluded = Boolean.parseBoolean(StringArgumentType.getString(context, "value"));
                                        PlayerStats stats = luck.excludeTop(target.uuid(), target.name(), excluded);
                                        Text.sendConfig(context.getSource().getSender(), config, "commands.luck-exclude", "{player}", stats.name(), "{state}", String.valueOf(stats.excludedFromTop()));
                                        return 1;
                                    }))));

            luckCmd.then(Commands.literal("giveitem")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((context, builder) -> suggestPlayers(builder))
                            .then(Commands.argument("luck", IntegerArgumentType.integer(0))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 2304))
                                            .executes(context -> {
                                                Player player = Bukkit.getPlayerExact(StringArgumentType.getString(context, "player"));
                                                if (player == null) {
                                                    Text.sendConfig(context.getSource().getSender(), config, "commands.player-not-found");
                                                    return 0;
                                                }
                                                int luckAmount = IntegerArgumentType.getInteger(context, "luck");
                                                int itemAmount = IntegerArgumentType.getInteger(context, "amount");
                                                plugin.scheduler().entity(player, () -> {
                                                    if (!player.isOnline()) return;
                                                    int remaining = itemAmount;
                                                    while (remaining > 0) {
                                                        var item = luckItems.build(player, luckAmount);
                                                        int stack = Math.min(item.getMaxStackSize(), remaining);
                                                        item.setAmount(stack);
                                                        player.getInventory().addItem(item).values()
                                                                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                                                        remaining -= stack;
                                                    }
                                                });
                                                Text.sendConfig(context.getSource().getSender(), config, "commands.luck-item-given",
                                                        "{amount}", String.valueOf(itemAmount), "{luck}", String.valueOf(luckAmount), "{player}", player.getName());
                                                return 1;
                                            })))));

            rootBuilder.then(luckCmd);

            event.registrar().register(plugin.getPluginMeta(), rootBuilder.build(), "ClientCore admin command", List.of("ccore"));
            event.registrar().register(legacyPacketMode(config, buildService).build(), "Start or stop ClientCore build capture", List.of());
            event.registrar().register(legacyPacketSave(config, buildService).build(), "Save a captured ClientCore build", List.of());
            event.registrar().register(legacyPacketApply(config, buildService).build(), "Apply or remove a ClientCore build", List.of());
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandTree(ClientCore plugin, ConfigManager config, ClientBuildService buildService) {
        return Commands.literal("build")
                .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                .then(Commands.literal("mode")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("on");
                                    builder.suggest("off");
                                    return builder.buildFuture();
                                })
                                .executes(context -> packetMode(config, buildService, context.getSource().getSender(), StringArgumentType.getString(context, "state")))))
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> packetSave(config, buildService, context.getSource().getSender(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("apply")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String value : buildService.buildNames()) {
                                        builder.suggest(value);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestPlayers(builder))
                                        .then(Commands.argument("state", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    builder.suggest("on");
                                                    builder.suggest("off");
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> packetApply(config, buildService, context.getSource().getSender(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "state")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> legacyPacketMode(ConfigManager config, ClientBuildService buildService) {
        return Commands.literal("packetmode")
                .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                .then(Commands.argument("state", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("on");
                            builder.suggest("off");
                            return builder.buildFuture();
                        })
                        .executes(context -> packetMode(config, buildService, context.getSource().getSender(), StringArgumentType.getString(context, "state"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> legacyPacketSave(ConfigManager config, ClientBuildService buildService) {
        return Commands.literal("packetsave")
                .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> packetSave(config, buildService, context.getSource().getSender(), StringArgumentType.getString(context, "name"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> legacyPacketApply(ConfigManager config, ClientBuildService buildService) {
        return Commands.literal("packetapply")
                .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (String value : buildService.buildNames()) {
                                builder.suggest(value);
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestPlayers(builder))
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("on");
                                            builder.suggest("off");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> packetApply(config, buildService, context.getSource().getSender(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "state"))))));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "auto" : value;
    }

    private static String hookState(boolean active) {
        return active ? "active" : "inactive";
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            builder.suggest(player.getName());
        }
        return builder.buildFuture();
    }

    private static int packetMode(ConfigManager config, ClientBuildService buildService, CommandSender sender, String state) {
        if (!(sender instanceof Player player)) {
            Text.sendConfig(sender, config, "commands.only-players");
            return 0;
        }
        if (state.equalsIgnoreCase("on")) {
            if (buildService.startSession(player)) {
                Text.sendConfig(player, config, "commands.client-build-mode-on");
                return 1;
            }
            Text.sendConfig(player, config, "commands.client-build-disabled");
            return 0;
        }
        if (state.equalsIgnoreCase("off")) {
            buildService.stopSession(player);
            Text.sendConfig(player, config, "commands.client-build-mode-off");
            return 1;
        }
        Text.sendConfig(player, config, "commands.client-build-mode-usage");
        return 0;
    }

    private static int packetSave(ConfigManager config, ClientBuildService buildService, CommandSender sender, String buildName) {
        if (!(sender instanceof Player player)) {
            Text.sendConfig(sender, config, "commands.only-players");
            return 0;
        }
        if (!buildService.hasSession(player)) {
            Text.sendConfig(player, config, "commands.client-build-save-needs-mode");
            return 0;
        }
        if (!buildService.saveSession(player, buildName)) {
            Text.sendConfig(player, config, "commands.client-build-save-empty");
            return 0;
        }
        Text.sendConfig(player, config, "commands.client-build-saved", "{build}", buildName);
        Text.sendConfig(player, config, "commands.client-build-mode-off");
        return 1;
    }

    private static int packetApply(ConfigManager config, ClientBuildService buildService, CommandSender sender, String buildName, String playerName, String state) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            Text.sendConfig(sender, config, "commands.player-not-found");
            return 0;
        }
        if (state.equalsIgnoreCase("on")) {
            if (!buildService.applyBuild(sender, target, buildName)) {
                return 0;
            }
            Text.sendConfig(sender, config, "commands.client-build-applied", "{build}", buildName, "{player}", target.getName());
            return 1;
        }
        if (state.equalsIgnoreCase("off")) {
            if (!buildService.removeBuild(target, buildName)) {
                Text.sendConfig(sender, config, "commands.client-build-not-applied", "{build}", buildName, "{player}", target.getName());
                return 0;
            }
            Text.sendConfig(sender, config, "commands.client-build-removed", "{build}", buildName, "{player}", target.getName());
            return 1;
        }
        Text.sendConfig(sender, config, "commands.client-build-apply-usage");
        return 0;
    }

    private static int spawnMythic(ClientCore plugin, ConfigManager config, ClientMobService mobService, CommandSender sender, String viewerInput, String mobId, double level, int amount) {
        Player viewer;
        if (viewerInput.equalsIgnoreCase("self")) {
            if (!(sender instanceof Player player)) {
                Text.sendConfig(sender, config, "commands.only-players");
                return 0;
            }
            viewer = player;
        } else {
            viewer = Bukkit.getPlayerExact(viewerInput);
            if (viewer == null) {
                Text.sendConfig(sender, config, "commands.player-not-found");
                return 0;
            }
        }

        plugin.scheduler().entity(viewer, () -> {
            for (int i = 0; i < amount; i++) {
                Location location = viewer.getLocation().add(viewer.getLocation().getDirection().normalize().multiply(3 + i));
                plugin.scheduler().region(location, () -> mobService.spawnMythicFor(viewer, location, mobId, level));
            }
        });
        Text.sendConfig(sender, config, "commands.mythic-mob-spawned",
                "{amount}", String.valueOf(amount), "{mob}", mobId, "{player}", viewer.getName(), "{level}", String.valueOf(level));
        return amount;
    }

    private static PlayerTarget target(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return new PlayerTarget(online.getUniqueId(), online.getName());
        }
        try {
            UUID uuid = UUID.fromString(input);
            return new PlayerTarget(uuid, input);
        } catch (IllegalArgumentException ignored) {
            return new PlayerTarget(UUID.nameUUIDFromBytes(("OfflinePlayer:" + input).getBytes(java.nio.charset.StandardCharsets.UTF_8)), input);
        }
    }

    private static void sendLuckTop(ClientCore plugin, ConfigManager config, CommandSender sender, LuckService luck, int limit) {
        luck.top(limit).thenAccept(stats -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            if (stats.isEmpty()) {
                Text.sendConfig(sender, config, "commands.luck-top-empty");
                return;
            }
            Text.sendConfig(sender, config, "commands.luck-top-header");
            int index = 1;
            for (PlayerStats row : stats) {
                Text.sendConfig(sender, config, "commands.luck-top-row",
                        "{rank}", String.valueOf(index), "{player}", row.name(), "{luck}", String.valueOf(row.luck()));
                index++;
            }
        }));
    }

    private record PlayerTarget(UUID uuid, String name) {
    }
}

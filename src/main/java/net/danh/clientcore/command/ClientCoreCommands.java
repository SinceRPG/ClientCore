package net.danh.clientcore.command;

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
import net.danh.clientcore.config.ConfigManager;
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

    public static void register(ClientCore plugin, ConfigManager configManager, BlockRegenService blockService, ClientMobService mobService, VisibilityService visibility, LuckService luck, LuckItemService luckItems) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, (ReloadableRegistrarEvent<Commands> event) -> {

            LiteralArgumentBuilder<CommandSourceStack> rootBuilder = Commands.literal("clientcore");

            rootBuilder.then(Commands.literal("reload")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .executes(context -> {
                        plugin.reloadPlugin();
                        Text.send(context.getSource().getSender(), "<green>ClientCore reloaded.");
                        return 1;
                    }));

            rootBuilder.then(Commands.literal("status")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .executes(context -> {
                        CommandSender sender = context.getSource().getSender();
                        Text.send(sender, "<aqua>ClientCore</aqua> <gray>blocks=" + blockService.ruleCount() + " mobs=" + mobService.ruleCount() + "</gray>");
                        return 1;
                    }));

            rootBuilder.then(Commands.literal("refresh")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .executes(context -> {
                        if (!(context.getSource().getSender() instanceof Player player)) {
                            Text.send(context.getSource().getSender(), "<red>Only players can refresh client blocks.");
                            return 0;
                        }
                        blockService.refreshAround(player);
                        Text.send(player, "<green>Refreshed client-side blocks around you.");
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
                                    Text.send(context.getSource().getSender(), "<red>Only players can use visibility.");
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
                                Text.send(player, hidden ? "<green>You are now hidden from other players." : "<green>You are now visible to other players.");
                                return 1;
                            })));

            rootBuilder.then(Commands.literal("mobspawn")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 50))
                            .executes(context -> {
                                if (!(context.getSource().getSender() instanceof Player player)) {
                                    Text.send(context.getSource().getSender(), "<red>Only players can spawn client mobs.");
                                    return 0;
                                }
                                int amount = IntegerArgumentType.getInteger(context, "amount");
                                for (int i = 0; i < amount; i++) {
                                    Location location = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(3 + i));
                                    plugin.scheduler().region(location, () -> mobService.spawnFor(player, location));
                                }
                                Text.send(player, "<green>Spawned " + amount + " client mob(s).");
                                return amount;
                            })));

            rootBuilder.then(Commands.literal("setspawn")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .then(Commands.argument("id", StringArgumentType.word())
                            .executes(context -> {
                                if (!(context.getSource().getSender() instanceof Player player)) {
                                    Text.send(context.getSource().getSender(), "<red>Only players can set mob spawns.");
                                    return 0;
                                }
                                String id = StringArgumentType.getString(context, "id");
                                SpawnPoint point = mobService.setSpawn(id, player.getLocation());
                                Text.send(player, "<green>Set spawn <white>" + point.id() + "</white> at your location.");
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
                                Text.send(context.getSource().getSender(), removed ? "<green>Deleted spawn " + id + "." : "<red>Spawn not found: " + id);
                                return removed ? 1 : 0;
                            })));

            rootBuilder.then(Commands.literal("spawns")
                    .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                    .executes(context -> {
                        CommandSender sender = context.getSource().getSender();
                        if (mobService.spawnList().isEmpty()) {
                            Text.send(sender, "<yellow>No client mob spawns configured.");
                            return 1;
                        }
                        for (SpawnPoint point : mobService.spawnList()) {
                            Text.send(sender, "<aqua>" + point.id() + "</aqua> <gray>rule=" + blank(point.rule()) + " amount=" + point.amount()
                                    + " batch=" + point.batchSize() + " radius=" + point.radius() + " interval=" + point.intervalTicks()
                                    + " maxAlive=" + point.maxAlive() + " enabled=" + point.enabled() + "</gray>");
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
                                                    Text.send(context.getSource().getSender(), "<red>Spawn not found: " + id);
                                                    return 0;
                                                }
                                                try {
                                                    point.set(attribute, value);
                                                    mobService.saveSpawns();
                                                    Text.send(context.getSource().getSender(), "<green>Updated " + id + " " + attribute + "=" + value + ".");
                                                    return 1;
                                                } catch (IllegalArgumentException ex) {
                                                    Text.send(context.getSource().getSender(), "<red>" + ex.getMessage());
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
                                configManager.getMain().set("settings.debug", enabled);
                                configManager.saveAll();
                                Text.send(context.getSource().getSender(), "<green>Debug set to " + enabled + ".");
                                return 1;
                            })));

            rootBuilder.then(Commands.literal("luck")
                    .then(Commands.literal("profile")
                            .requires(source -> source.getSender().hasPermission("clientcore.luck"))
                            .executes(context -> {
                                if (!(context.getSource().getSender() instanceof Player player)) {
                                    Text.send(context.getSource().getSender(), "<red>Console must specify a player with admin luck commands.");
                                    return 0;
                                }
                                PlayerStats stats = luck.snapshot(player);
                                Text.send(player, "<aqua>Luck</aqua><gray>: <white>" + stats.luck() + "</white></gray>");
                                return 1;
                            }))
                    .then(Commands.literal("top")
                            .requires(source -> source.getSender().hasPermission("clientcore.luck"))
                            .executes(context -> {
                                sendLuckTop(plugin, context.getSource().getSender(), luck, 10);
                                return 1;
                            }))
                    .then(Commands.literal("t")
                            .requires(source -> source.getSender().hasPermission("clientcore.luck"))
                            .executes(context -> {
                                sendLuckTop(plugin, context.getSource().getSender(), luck, 10);
                                return 1;
                            }))
                    .then(Commands.literal("set")
                            .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests((context, builder) -> suggestPlayers(builder))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                            .executes(context -> {
                                                PlayerTarget target = target(StringArgumentType.getString(context, "player"));
                                                PlayerStats stats = luck.set(target.uuid(), target.name(), IntegerArgumentType.getInteger(context, "amount"));
                                                Text.send(context.getSource().getSender(), "<green>Set " + stats.name() + " luck to " + stats.luck() + ".");
                                                return 1;
                                            }))))
                    .then(Commands.literal("add")
                            .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests((context, builder) -> suggestPlayers(builder))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer())
                                            .executes(context -> {
                                                PlayerTarget target = target(StringArgumentType.getString(context, "player"));
                                                PlayerStats stats = luck.add(target.uuid(), target.name(), IntegerArgumentType.getInteger(context, "amount"));
                                                Text.send(context.getSource().getSender(), "<green>Added luck. " + stats.name() + " now has " + stats.luck() + ".");
                                                return 1;
                                            }))))
                    .then(Commands.literal("remove")
                            .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests((context, builder) -> suggestPlayers(builder))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                            .executes(context -> {
                                                PlayerTarget target = target(StringArgumentType.getString(context, "player"));
                                                PlayerStats stats = luck.add(target.uuid(), target.name(), -IntegerArgumentType.getInteger(context, "amount"));
                                                Text.send(context.getSource().getSender(), "<green>Removed luck. " + stats.name() + " now has " + stats.luck() + ".");
                                                return 1;
                                            }))))
                    .then(Commands.literal("reset")
                            .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests((context, builder) -> suggestPlayers(builder))
                                    .executes(context -> {
                                        PlayerTarget target = target(StringArgumentType.getString(context, "player"));
                                        PlayerStats stats = luck.set(target.uuid(), target.name(), 0.0D);
                                        Text.send(context.getSource().getSender(), "<green>Reset " + stats.name() + " luck.");
                                        return 1;
                                    })))
                    .then(Commands.literal("top-exclude")
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
                                                Text.send(context.getSource().getSender(), "<green>Top exclusion for " + stats.name() + " = " + stats.excludedFromTop() + ".");
                                                return 1;
                                            }))))
                    .then(Commands.literal("giveitem")
                            .requires(source -> source.getSender().hasPermission("clientcore.admin"))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests((context, builder) -> suggestPlayers(builder))
                                    .then(Commands.argument("luck", IntegerArgumentType.integer(0))
                                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 2304))
                                                    .executes(context -> {
                                                        Player player = Bukkit.getPlayerExact(StringArgumentType.getString(context, "player"));
                                                        if (player == null) {
                                                            Text.send(context.getSource().getSender(), "<red>Player must be online to receive a luck item.");
                                                            return 0;
                                                        }
                                                        int luckAmount = IntegerArgumentType.getInteger(context, "luck");
                                                        int itemAmount = IntegerArgumentType.getInteger(context, "amount");
                                                        int remaining = itemAmount;
                                                        while (remaining > 0) {
                                                            var item = luckItems.build(player, luckAmount);
                                                            int stack = Math.min(item.getMaxStackSize(), remaining);
                                                            item.setAmount(stack);
                                                            player.give(item);
                                                            remaining -= stack;
                                                        }
                                                        Text.send(context.getSource().getSender(), "<green>Gave " + itemAmount + " luck item(s) worth " + luckAmount + " luck to " + player.getName() + ".");
                                                        return 1;
                                                    }))))));

            event.registrar().register(plugin.getPluginMeta(), rootBuilder.build(), "ClientCore admin command", List.of("ccore"));
        });
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "auto" : value;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            builder.suggest(player.getName());
        }
        return builder.buildFuture();
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

    private static void sendLuckTop(ClientCore plugin, CommandSender sender, LuckService luck, int limit) {
        luck.top(limit).thenAccept(stats -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            if (stats.isEmpty()) {
                Text.send(sender, "<yellow>No luck records found.");
                return;
            }
            Text.send(sender, "<aqua>Luck Top</aqua>");
            int index = 1;
            for (PlayerStats row : stats) {
                Text.send(sender, "<gray>#" + index + " <white>" + row.name() + "</white> <aqua>" + row.luck() + "</aqua></gray>");
                index++;
            }
        }));
    }

    private record PlayerTarget(UUID uuid, String name) {
    }
}
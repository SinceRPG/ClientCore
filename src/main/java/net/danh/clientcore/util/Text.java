package net.danh.clientcore.util;

import net.danh.clientcore.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String input) {
        return MINI.deserialize(input == null ? "" : input);
    }

    public static void send(CommandSender sender, String input) {
        if (input == null || input.isBlank()) return;
        sender.sendMessage(mm(input));
    }

    public static void sendConfig(CommandSender sender, ConfigManager config, String path, String... replacements) {
        String prefix = config.getMessages().getString("prefix", "");
        String msg = config.getMessages().getString(path);
        if (msg == null || msg.isBlank()) return;
        for (int i = 0; i < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        send(sender, prefix + msg);
    }

    public static void log(Plugin plugin, ConfigManager config, String path, String... replacements) {
        String msg = config.getMessages().getString(path);
        if (msg == null || msg.isBlank()) return;
        for (int i = 0; i < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        plugin.getLogger().info(msg);
    }

    public static void warn(Plugin plugin, ConfigManager config, String path, String... replacements) {
        String msg = config.getMessages().getString(path);
        if (msg == null || msg.isBlank()) return;
        for (int i = 0; i < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        plugin.getLogger().warning(msg);
    }
}
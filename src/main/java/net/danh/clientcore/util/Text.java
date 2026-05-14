package net.danh.clientcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String input) {
        return MINI.deserialize(input == null ? "" : input);
    }

    public static void send(CommandSender sender, String input) {
        sender.sendMessage(mm(input));
    }
}

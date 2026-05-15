package net.danh.clientcore.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

public final class ConfigManager {
    private final Plugin plugin;
    private YamlConfiguration mainConfig;
    private YamlConfiguration blocksConfig;
    private YamlConfiguration mobsConfig;
    private YamlConfiguration npcsConfig;
    private YamlConfiguration dropsConfig;
    private YamlConfiguration chestsConfig;
    private YamlConfiguration messagesConfig;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        mainConfig = load("config.yml");
        blocksConfig = load("blocks.yml");
        mobsConfig = load("mobs.yml");
        npcsConfig = load("npcs.yml");
        dropsConfig = load("drops.yml");
        chestsConfig = load("chests.yml");
        messagesConfig = load("messages.yml");
    }

    public void saveAll() {
        save(mainConfig, "config.yml");
        save(blocksConfig, "blocks.yml");
        save(mobsConfig, "mobs.yml");
        save(npcsConfig, "npcs.yml");
        save(dropsConfig, "drops.yml");
        save(chestsConfig, "chests.yml");
        save(messagesConfig, "messages.yml");
    }

    private YamlConfiguration load(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void save(YamlConfiguration config, String fileName) {
        if (config == null) return;
        try {
            config.save(new File(plugin.getDataFolder(), fileName));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save " + fileName);
        }
    }

    public YamlConfiguration getMain() {
        return mainConfig;
    }

    public YamlConfiguration getBlocks() {
        return blocksConfig;
    }

    public YamlConfiguration getMobs() {
        return mobsConfig;
    }

    public YamlConfiguration getNpcs() {
        return npcsConfig;
    }

    public YamlConfiguration getDrops() {
        return dropsConfig;
    }

    public YamlConfiguration getChests() {
        return chestsConfig;
    }

    public YamlConfiguration getMessages() {
        return messagesConfig;
    }
}
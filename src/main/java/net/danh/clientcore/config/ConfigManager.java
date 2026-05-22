package net.danh.clientcore.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ConfigManager {
    private static final String CONFIG_FILE = "config.yml";
    private static final String MESSAGES_FILE = "messages.yml";
    private static final String BLOCKS_FOLDER = "blocks";
    private static final String MOBS_FOLDER = "mobs";
    private static final String NPCS_FOLDER = "npcs";
    private static final String DROPS_FOLDER = "drops";
    private static final String CHESTS_FOLDER = "chests";
    private static final String BLOCKS_FILE = "blocks/blocks.yml";
    private static final String MOBS_FILE = "mobs/mobs.yml";
    private static final String NPCS_FILE = "npcs/npcs.yml";
    private static final String DROPS_FILE = "drops/drops.yml";
    private static final String CHESTS_FILE = "chests/chests.yml";

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
        mainConfig = load(CONFIG_FILE, true);
        blocksConfig = loadFeatureFolder(BLOCKS_FOLDER, BLOCKS_FILE, "blocks.yml", "block-regen");
        mobsConfig = loadFeatureFolder(MOBS_FOLDER, MOBS_FILE, "mobs.yml", "client-mobs");
        npcsConfig = loadFeatureFolder(NPCS_FOLDER, NPCS_FILE, "npcs.yml", "client-npcs");
        dropsConfig = loadFeatureFolder(DROPS_FOLDER, DROPS_FILE, "drops.yml", "client-drops");
        chestsConfig = loadFeatureFolder(CHESTS_FOLDER, CHESTS_FILE, "chests.yml", "client-loot-chests");
        messagesConfig = load(MESSAGES_FILE, true);
    }

    public void saveAll() {
        save(mainConfig, CONFIG_FILE);
        save(messagesConfig, MESSAGES_FILE);
    }

    private YamlConfiguration load(String fileName) {
        return load(fileName, false);
    }

    private YamlConfiguration load(String fileName, boolean updateMissingKeys) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (updateMissingKeys) {
            addMissingDefaults(config, fileName);
        }
        return config;
    }

    private YamlConfiguration loadFeatureFolder(String folderName, String defaultFileName, String legacyFileName, String rootPath) {
        migrateLegacyFile(defaultFileName, legacyFileName);
        ensureResourceFile(defaultFileName);

        YamlConfiguration aggregate = new YamlConfiguration();
        File legacy = new File(plugin.getDataFolder(), legacyFileName);
        if (legacy.exists()) {
            mergeFeatureConfig(aggregate, YamlConfiguration.loadConfiguration(legacy), rootPath);
        }
        for (File file : yamlFiles(folderName)) {
            YamlConfiguration source = YamlConfiguration.loadConfiguration(file);
            mergeFeatureConfig(aggregate, source, rootPath);
        }
        return aggregate;
    }

    private void mergeFeatureConfig(YamlConfiguration target, YamlConfiguration source, String rootPath) {
        ConfigurationSection root = source.getConfigurationSection(rootPath);
        if (root != null) {
            mergeSection(target, rootPath, root);
            return;
        }

        ConfigurationSection rules = source.getConfigurationSection("rules");
        if (rules != null) {
            for (String key : source.getKeys(false)) {
                Object value = source.get(key);
                String path = rootPath + "." + key;
                if (value instanceof ConfigurationSection section) {
                    mergeSection(target, path, section);
                } else {
                    target.set(path, value);
                }
            }
            return;
        }

        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            String path = rootPath + ".rules." + key;
            if (value instanceof ConfigurationSection section) {
                mergeSection(target, path, section);
            } else {
                target.set(path, value);
            }
        }
    }

    private void mergeSection(YamlConfiguration target, String path, ConfigurationSection source) {
        if (target.getConfigurationSection(path) == null) {
            target.createSection(path);
        }
        for (String key : source.getKeys(false)) {
            String childPath = path.isBlank() ? key : path + "." + key;
            Object value = source.get(key);
            if (value instanceof ConfigurationSection section) {
                mergeSection(target, childPath, section);
            } else {
                target.set(childPath, value);
            }
        }
    }

    private List<File> yamlFiles(String folderName) {
        File folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists()) return List.of();
        List<File> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(folder.toPath())) {
            paths.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().endsWith(".yml") || file.getName().endsWith(".yaml"))
                    .sorted(Comparator.comparing(File::getPath, String.CASE_INSENSITIVE_ORDER))
                    .forEach(files::add);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load YAML files from " + folderName);
        }
        return files;
    }

    private void ensureResourceFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (file.exists()) return;
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            plugin.saveResource(fileName, false);
        } catch (IllegalArgumentException | IOException e) {
            plugin.getLogger().warning("Failed to create default " + fileName);
        }
    }

    private void addMissingDefaults(YamlConfiguration config, String fileName) {
        try (InputStream stream = plugin.getResource(fileName)) {
            if (stream == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            save(config, fileName);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to update missing keys in " + fileName);
        }
    }

    private void migrateLegacyFile(String fileName, String legacyFileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        File legacy = new File(plugin.getDataFolder(), legacyFileName);
        if (file.exists() || !legacy.exists()) return;
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.move(legacy.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to migrate " + legacyFileName + " to " + fileName);
        }
    }

    private void save(YamlConfiguration config, String fileName) {
        if (config == null) return;
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            config.save(file);
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

package net.danh.clientcore.mob;

import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.storage.StorageService;
import net.danh.clientcore.util.Text;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnPointStore {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final File file;
    private final StorageService storage;
    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();

    public SpawnPointStore(Plugin plugin, ConfigManager configManager, StorageService storage) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storage = storage;
        this.file = new File(plugin.getDataFolder(), "spawns.yml");
    }

    public void load() {
        points.clear();
        List<SpawnPoint> sqlPoints = storage.loadSpawns().join();
        for (SpawnPoint point : sqlPoints) {
            points.put(point.id(), point);
        }
        if (!points.isEmpty()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("spawns");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            World world = plugin.getServer().getWorld(section.getString("world", "world"));
            if (world == null) {
                Text.warn(plugin, configManager, "console.world-not-found", "{rule}", id, "{world}", section.getString("world"));
                continue;
            }
            Location location = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
            SpawnPoint point = new SpawnPoint(id, location);
            for (String key : section.getKeys(false)) {
                if (!key.equals("world") && !key.equals("x") && !key.equals("y") && !key.equals("z")) {
                    point.set(key, String.valueOf(section.get(key)));
                }
            }
            points.put(point.id(), point);
        }
        if (!points.isEmpty()) {
            storage.saveSpawns(List.copyOf(points.values()));
            Text.log(plugin, configManager, "console.migrated-spawns", "{count}", String.valueOf(points.size()));
        }
    }

    public void save() {
        storage.saveSpawns(List.copyOf(points.values()));
    }

    public SpawnPoint set(String id, Location location) {
        SpawnPoint point = new SpawnPoint(id, location);
        points.put(point.id(), point);
        storage.saveSpawn(point);
        return point;
    }

    public boolean delete(String id) {
        boolean removed = points.remove(id.toLowerCase()) != null;
        if (removed) {
            storage.deleteSpawn(id);
        }
        return removed;
    }

    public Optional<SpawnPoint> get(String id) {
        return Optional.ofNullable(points.get(id.toLowerCase()));
    }

    public Collection<SpawnPoint> all() {
        return points.values();
    }
}
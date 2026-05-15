package net.danh.clientcore.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.danh.clientcore.config.ConfigManager;
import net.danh.clientcore.mob.SpawnPoint;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StorageService implements AutoCloseable {
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ExecutorService executor;
    private HikariDataSource dataSource;
    private boolean mysql;

    public StorageService(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "ClientCore-SQL");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static PlayerStats read(ResultSet result) throws SQLException {
        return new PlayerStats(
                UUID.fromString(result.getString("uuid")),
                result.getString("name"),
                result.getDouble("luck"),
                result.getBoolean("excluded_top")
        );
    }

    public void start() {
        if (dataSource != null) return;
        HikariConfig config = new HikariConfig();
        ConfigurationSection storage = configManager.getMain().getConfigurationSection("storage");
        String type = storage == null ? "sqlite" : storage.getString("type", "sqlite").toLowerCase(Locale.ROOT);
        mysql = type.equals("mysql");

        if (mysql) {
            ConfigurationSection mysqlSec = storage.getConfigurationSection("mysql");
            String host = mysqlSec == null ? "localhost" : mysqlSec.getString("host", "localhost");
            int port = mysqlSec == null ? 3306 : mysqlSec.getInt("port", 3306);
            String database = mysqlSec == null ? "clientcore" : mysqlSec.getString("database", "clientcore");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&characterEncoding=utf8&useUnicode=true");
            config.setUsername(mysqlSec == null ? "root" : mysqlSec.getString("username", "root"));
            config.setPassword(mysqlSec == null ? "" : mysqlSec.getString("password", ""));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            File file = new File(plugin.getDataFolder(), storage == null ? "clientcore.db" : storage.getString("sqlite.file", "clientcore.db"));
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
        }

        config.setPoolName("ClientCorePool");
        config.setMaximumPoolSize(Math.max(1, storage == null ? 4 : storage.getInt("pool.maximum-size", 4)));
        config.setMinimumIdle(Math.max(0, storage == null ? 1 : storage.getInt("pool.minimum-idle", 1)));
        config.setConnectionTimeout(Math.max(1000L, storage == null ? 10000L : storage.getLong("pool.connection-timeout-ms", 10000L)));
        dataSource = new HikariDataSource(config);
        runSchema();
    }

    public CompletableFuture<PlayerStats> loadPlayer(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement select = connection.prepareStatement("SELECT uuid, name, luck, excluded_top FROM clientcore_player_stats WHERE uuid=?")) {
                    select.setString(1, uuid.toString());
                    try (ResultSet result = select.executeQuery()) {
                        if (result.next()) {
                            PlayerStats stats = read(result);
                            if (name != null && !name.isBlank() && !name.equals(stats.name())) {
                                PlayerStats renamed = stats.withName(name);
                                saveNow(connection, renamed);
                                return renamed;
                            }
                            return stats;
                        }
                    }
                }
                PlayerStats created = new PlayerStats(uuid, name == null ? uuid.toString() : name, 0.0D, false);
                saveNow(connection, created);
                return created;
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to load ClientCore player stats", ex);
            }
        }, executor);
    }

    public CompletableFuture<Void> savePlayer(PlayerStats stats) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                saveNow(connection, stats);
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to save ClientCore player stats", ex);
            }
        }, executor);
    }

    public CompletableFuture<List<PlayerStats>> topLuck(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerStats> result = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT uuid, name, luck, excluded_top FROM clientcore_player_stats WHERE excluded_top=0 AND luck > 0 ORDER BY luck DESC LIMIT ?")) {
                statement.setInt(1, Math.max(1, limit));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(read(rows));
                    }
                }
                return result;
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to load ClientCore luck top", ex);
            }
        }, executor);
    }

    public CompletableFuture<List<SpawnPoint>> loadSpawns() {
        return CompletableFuture.supplyAsync(() -> {
            List<SpawnPoint> result = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT id, world, x, y, z, rule, amount, radius, batch_size, interval_ticks, max_alive, level, activation_range, enabled
                         FROM clientcore_spawns ORDER BY id
                         """);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new SpawnPoint(
                            rows.getString("id"),
                            rows.getString("world"),
                            rows.getDouble("x"),
                            rows.getDouble("y"),
                            rows.getDouble("z"),
                            rows.getString("rule"),
                            rows.getInt("amount"),
                            rows.getDouble("radius"),
                            rows.getInt("batch_size"),
                            rows.getInt("interval_ticks"),
                            rows.getInt("max_alive"),
                            rows.getDouble("level"),
                            rows.getDouble("activation_range"),
                            rows.getBoolean("enabled")
                    ));
                }
                return result;
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to load ClientCore spawns", ex);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveSpawn(SpawnPoint point) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                saveSpawnNow(connection, point);
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to save ClientCore spawn", ex);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveSpawns(List<SpawnPoint> points) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                for (SpawnPoint point : points) {
                    saveSpawnNow(connection, point);
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to save ClientCore spawns", ex);
            }
        }, executor);
    }

    public CompletableFuture<Void> deleteSpawn(String id) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM clientcore_spawns WHERE id=?")) {
                statement.setString(1, id.toLowerCase(Locale.ROOT));
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to delete ClientCore spawn", ex);
            }
        }, executor);
    }

    private void runSchema() {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS clientcore_player_stats (
                          uuid VARCHAR(36) PRIMARY KEY,
                          name VARCHAR(64) NOT NULL,
                          luck DOUBLE NOT NULL DEFAULT 0,
                          excluded_top BOOLEAN NOT NULL DEFAULT 0,
                          updated_at BIGINT NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS clientcore_spawns (
                          id VARCHAR(64) PRIMARY KEY,
                          world VARCHAR(128) NOT NULL,
                          x DOUBLE NOT NULL,
                          y DOUBLE NOT NULL,
                          z DOUBLE NOT NULL,
                          rule VARCHAR(128) NOT NULL DEFAULT '',
                          amount INT NOT NULL DEFAULT 1,
                          radius DOUBLE NOT NULL DEFAULT 4,
                          batch_size INT NOT NULL DEFAULT 1,
                          interval_ticks INT NOT NULL DEFAULT 100,
                          max_alive INT NOT NULL DEFAULT 8,
                          level DOUBLE NOT NULL DEFAULT 1,
                          activation_range DOUBLE NOT NULL DEFAULT 48,
                          enabled BOOLEAN NOT NULL DEFAULT 1,
                          updated_at BIGINT NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS clientcore_cooldowns (
                                uuid VARCHAR(36) NOT NULL,
                                category VARCHAR(32) NOT NULL,
                                rule_id VARCHAR(64) NOT NULL,
                                expires_at BIGINT NOT NULL,
                                PRIMARY KEY (uuid, category, rule_id)
                            )
                        """);
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to initialize ClientCore SQL schema", ex);
            }
        }, executor).join();
    }

    private void saveNow(Connection connection, PlayerStats stats) throws SQLException {
        String sql = mysql ? """
                INSERT INTO clientcore_player_stats(uuid, name, luck, excluded_top, updated_at)
                VALUES(?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE name=VALUES(name), luck=VALUES(luck), excluded_top=VALUES(excluded_top), updated_at=VALUES(updated_at)
                """ : """
                INSERT INTO clientcore_player_stats(uuid, name, luck, excluded_top, updated_at)
                VALUES(?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, luck=excluded.luck, excluded_top=excluded.excluded_top, updated_at=excluded.updated_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stats.uuid().toString());
            statement.setString(2, stats.name());
            statement.setDouble(3, stats.luck());
            statement.setBoolean(4, stats.excludedFromTop());
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void saveSpawnNow(Connection connection, SpawnPoint point) throws SQLException {
        String sql = mysql ? """
                INSERT INTO clientcore_spawns(id, world, x, y, z, rule, amount, radius, batch_size, interval_ticks, max_alive, level, activation_range, enabled, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), rule=VALUES(rule),
                amount=VALUES(amount), radius=VALUES(radius), batch_size=VALUES(batch_size), interval_ticks=VALUES(interval_ticks),
                max_alive=VALUES(max_alive), level=VALUES(level), activation_range=VALUES(activation_range), enabled=VALUES(enabled), updated_at=VALUES(updated_at)
                """ : """
                INSERT INTO clientcore_spawns(id, world, x, y, z, rule, amount, radius, batch_size, interval_ticks, max_alive, level, activation_range, enabled, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, rule=excluded.rule,
                amount=excluded.amount, radius=excluded.radius, batch_size=excluded.batch_size, interval_ticks=excluded.interval_ticks,
                max_alive=excluded.max_alive, level=excluded.level, activation_range=excluded.activation_range, enabled=excluded.enabled, updated_at=excluded.updated_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, point.id());
            statement.setString(2, point.world());
            statement.setDouble(3, point.x());
            statement.setDouble(4, point.y());
            statement.setDouble(5, point.z());
            statement.setString(6, point.rule());
            statement.setInt(7, point.amount());
            statement.setDouble(8, point.radius());
            statement.setInt(9, point.batchSize());
            statement.setInt(10, point.intervalTicks());
            statement.setInt(11, point.maxAlive());
            statement.setDouble(12, point.level());
            statement.setDouble(13, point.activationRange());
            statement.setBoolean(14, point.enabled());
            statement.setLong(15, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Map<String, Long>> loadCooldowns(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Long> map = new java.util.concurrent.ConcurrentHashMap<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT category, rule_id, expires_at FROM clientcore_cooldowns WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        long expires = result.getLong("expires_at");
                        if (System.currentTimeMillis() < expires) {
                            map.put(result.getString("category") + ":" + result.getString("rule_id"), expires);
                        } else {
                            cleanupCooldown(connection, uuid, result.getString("category"), result.getString("rule_id"));
                        }
                    }
                }
                return map;
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to load ClientCore cooldowns", ex);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveCooldown(UUID uuid, String category, String ruleId, long expiresAt) {
        return CompletableFuture.runAsync(() -> {
            String sql = mysql ? """
                    INSERT INTO clientcore_cooldowns(uuid, category, rule_id, expires_at) VALUES(?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)
                    """ : """
                    INSERT INTO clientcore_cooldowns(uuid, category, rule_id, expires_at) VALUES(?, ?, ?, ?)
                    ON CONFLICT(uuid, category, rule_id) DO UPDATE SET expires_at=excluded.expires_at
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, category);
                statement.setString(3, ruleId);
                statement.setLong(4, expiresAt);
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to save ClientCore cooldown", ex);
            }
        }, executor);
    }

    private void cleanupCooldown(Connection connection, UUID uuid, String category, String ruleId) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clientcore_cooldowns WHERE uuid=? AND category=? AND rule_id=?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, category);
            statement.setString(3, ruleId);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
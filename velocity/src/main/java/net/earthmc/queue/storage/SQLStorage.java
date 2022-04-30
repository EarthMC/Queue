package net.earthmc.queue.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class SQLStorage extends Storage {
    private final QueuePlugin plugin;
    private HikariDataSource dataSource;
    private boolean enabled = false;

    public SQLStorage(@NotNull QueuePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled)
            return;

        plugin.logger().info("Enabling SQL storage.");

        String connectionUrl = "jdbc:mysql://" + plugin.config().getDatabaseHost() + ":" + plugin.config().getDatabasePort() + "/" + plugin.config().getDatabaseName() + plugin.config().getDatabaseFlags();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionUrl);
        config.setUsername(plugin.config().getDatabaseUsername());
        config.setPassword(plugin.config().getDatabasePassword());
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(1000);

        try {
            DriverManager.registerDriver((Driver) Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }

        dataSource = new HikariDataSource(config);

        try {
            dataSource.getConnection();
            plugin.logger().info("Successfully connected to the database.");
        } catch (SQLException e) {
            plugin.logger().error("Exception occurred when connecting to database", e);
            return;
        }

        // create default table
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("create table if not exists queue_players (`uuid` varchar(36) not null, primary key (`uuid`))");

            for (String column : SQLSchema.getPlayerColumns()) {
                connection.createStatement().execute("alter table queue_players add column if not exists " + column);
            }
        } catch (SQLException e) {
            plugin.logger().error("An exception occurred when initializing queue_players table", e);
        }
    }

    @Override
    public void disable() {
        if (!this.enabled)
            return;

        this.enabled = false;

        if (dataSource != null && dataSource.isRunning()) {
            dataSource.close();
            dataSource = null;
        }
    }

    @Override
    public void loadPlayer(@NotNull QueuedPlayer player) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement("SELECT * FROM queue_players WHERE uuid = ? LIMIT 1")) {
                ps.setString(1, player.uuid().toString());

                try (ResultSet resultSet = ps.executeQuery()) {
                    if (resultSet.next()) {
                        player.setLastJoinedServer(resultSet.getString("lastJoinedServer"));
                        player.setAutoQueueDisabled(resultSet.getBoolean("autoQueueDisabled"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void savePlayer(@NotNull QueuedPlayer player) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement("replace into queue_players (uuid, autoQueueDisabled, lastJoinedServer) values (?, ?, ?)")) {
                ps.setString(1, player.uuid().toString());
                ps.setBoolean(2, player.isAutoQueueDisabled());
                ps.setString(3, player.getLastJoinedServer().orElse(null));

                ps.execute();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}

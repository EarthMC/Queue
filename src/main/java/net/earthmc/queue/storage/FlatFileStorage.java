package net.earthmc.queue.storage;

import net.earthmc.queue.PlayerData;
import net.earthmc.queue.QueuePlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FlatFileStorage extends Storage {
    private final QueuePlugin plugin;
    private final Path dataFolderPath; // Path to velocity /plugins/queue/data

    public FlatFileStorage(QueuePlugin plugin, Path dataFolderPath) {
        this.plugin = plugin;
        this.dataFolderPath = dataFolderPath;

        if (!Files.isDirectory(dataFolderPath)) {
            try {
                Files.createDirectory(dataFolderPath);
            } catch (IOException e) {
                plugin.logger().error("Couldn't create the Queue/data directory.", e);
            }
        }

        plugin.logger().info("Using flatfile storage.");
    }

    @Override
    public CompletableFuture<PlayerData> loadPlayer(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            final Path dataFile = dataFolderPath.resolve(uuid + ".txt");

            if (Files.exists(dataFile)) {
                Properties properties = new Properties();
                try (InputStream is = Files.newInputStream(dataFile)) {
                    properties.load(is);
                    return new PlayerData(
                        Boolean.parseBoolean(properties.getProperty("autoQueueDisabled", "false")),
                        properties.getProperty("lastJoinedServer")
                    );
                } catch (IOException ignored) {}
            }

            return new PlayerData(false, null);
        });
    }

    @Override
    public CompletableFuture<Void> savePlayer(@NotNull UUID uuid, PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path dataFile = dataFolderPath.resolve(uuid + ".txt");

                Properties properties = new Properties();
                data.getLastJoinedServer().ifPresent(server -> properties.setProperty("lastJoinedServer", server));

                properties.setProperty("autoQueueDisabled", String.valueOf(data.isAutoQueueDisabled()));

                try (OutputStream os = Files.newOutputStream(dataFile)) {
                    properties.store(os, null);
                }
            } catch (IOException e) {
                plugin.logger().error("An error occurred when saving data for {}", uuid, e);
            }
        });
    }
}

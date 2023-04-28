package net.earthmc.queue.storage;

import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
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
    public CompletableFuture<Void> loadPlayer(@NotNull QueuedPlayer player) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path dataFile = dataFolderPath.resolve(player.uuid() + ".txt");

                if (!Files.exists(dataFile))
                    return;

                Properties properties = new Properties();
                try (InputStream is = Files.newInputStream(dataFile)) {
                    properties.load(is);
                    player.setAutoQueueDisabled(Boolean.parseBoolean(properties.getProperty("autoQueueDisabled", "false")));
                    player.setLastJoinedServer(properties.getProperty("lastJoinedServer"));
                }
            } catch (IOException ignored) {}
        });
    }

    @Override
    public CompletableFuture<Void> savePlayer(@NotNull QueuedPlayer player) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path dataFile = dataFolderPath.resolve(player.uuid() + ".txt");

                Properties properties = new Properties();
                if (player.getLastJoinedServer().isPresent())
                    properties.setProperty("lastJoinedServer", player.getLastJoinedServer().get());

                properties.setProperty("autoQueueDisabled", String.valueOf(player.isAutoQueueDisabled()));

                try (OutputStream os = Files.newOutputStream(dataFile)) {
                    properties.store(os, null);
                }
            } catch (IOException e) {
                plugin.logger().error("An error occurred when saving data for " + player.uuid(), e);
            }
        });
    }
}

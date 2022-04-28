package net.earthmc.queue.storage;

import com.google.common.collect.Maps;
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

public class FlatFileStorage {
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
    }

    public CompletableFuture<Void> loadSavedData(@NotNull QueuedPlayer player) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path dataFile = dataFolderPath.resolve(player.uuid() + ".txt");

                if (!Files.exists(dataFile))
                    return;

                Properties properties = new Properties();
                try (InputStream is = Files.newInputStream(dataFile)) {
                    properties.load(is);
                    player.getSettings().putAll(Maps.fromProperties(properties));
                }
            } catch (IOException ignored) {}
        });
    }

    public void savePlayer(@NotNull QueuedPlayer player) {
        CompletableFuture.runAsync(() -> {
            try {
                Path dataFile = dataFolderPath.resolve(player.uuid() + ".txt");

                if (!Files.exists(dataFile))
                    Files.createFile(dataFile);

                Properties properties = new Properties();
                properties.putAll(player.getSettings());

                try (OutputStream os = Files.newOutputStream(dataFile)) {
                    properties.store(os, null);
                }
            } catch (IOException e) {
                plugin.logger().error("An error occurred when saving data for " + player.uuid(), e);
            }
        });
    }
}

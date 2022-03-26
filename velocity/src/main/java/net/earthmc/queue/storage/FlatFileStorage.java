package net.earthmc.queue.storage;

import net.earthmc.queue.QueuePlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FlatFileStorage {
    private final QueuePlugin plugin;
    private final Path dataFolderPath; // Path to velocity root/queue/data

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

    public CompletableFuture<Optional<String>> getLastJoinedServer(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path dataFile = dataFolderPath.resolve(uuid + ".txt");

                if (!Files.exists(dataFile))
                    return Optional.empty();

                Properties properties = new Properties();
                try (InputStream is = Files.newInputStream(dataFile)) {
                    properties.load(is);
                    return Optional.of(properties.getProperty("lastJoined"));
                }
            } catch (IOException e) {
                return Optional.empty();
            }
        });
    }

    public void setLastJoinedServer(@NotNull UUID uuid, @NotNull String lastJoined) {
        CompletableFuture.runAsync(() -> {
            try {
                Path dataFile = dataFolderPath.resolve(uuid + ".txt");

                if (!Files.exists(dataFile))
                    Files.createFile(dataFile);

                Properties properties = new Properties();
                properties.put("lastJoined", lastJoined);

                try (OutputStream os = Files.newOutputStream(dataFile)) {
                    properties.store(os, null);
                }
            } catch (IOException ignored) {}
        });
    }
}

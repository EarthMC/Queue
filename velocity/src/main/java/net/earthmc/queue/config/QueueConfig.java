package net.earthmc.queue.config;

import com.moandjiezana.toml.Toml;
import net.earthmc.queue.Priority;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.earthmc.queue.SubQueue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class QueueConfig {
    private final QueuePlugin plugin;
    private final Path dataFolder;
    private final Path configPath;
    private Toml config;
    private Set<Priority> priorities;
    private Set<SubQueue> subQueues;
    private AutoQueueSettings autoQueueSettings;
    private static final String CONFIG_FILE_NAME = "config.toml";

    public QueueConfig(QueuePlugin plugin, Path pluginFolder) {
        this.plugin = plugin;
        this.dataFolder = pluginFolder;
        this.configPath = pluginFolder.resolve(CONFIG_FILE_NAME);
    }

    public boolean load() {
        saveDefaultConfig();

        config = new Toml().read(configPath.toFile());
        priorities = new ConcurrentSkipListSet<>();
        subQueues = new ConcurrentSkipListSet<>();

        plugin.setDebug(config.getBoolean("debug", false));

        Toml autoQueueConfig = config.getTable("autoqueue");
        this.autoQueueSettings = new AutoQueueSettings(autoQueueConfig.getLong("delay"), autoQueueConfig.getString("default-target"), autoQueueConfig.getString("autoqueue-server"));

        for (Toml priority : config.getTables("priority")) {
            String name = priority.getString("name", "none");
            long weight = priority.getLong("weight", 0L);
            Component message = MiniMessage.miniMessage().deserialize(priority.getString("message", ""));

            priorities.add(new Priority(name, Math.max((int) weight, 0), message));
            QueuePlugin.debug("Added new priority with name " + name + ".");
        }

        boolean hasRegularQueue = false;
        for (Toml subQueue : config.getTables("subqueue")) {
            String name = subQueue.getString("name", "regular");
            long weight = subQueue.getLong("min-weight", 0L);
            long maxSends = subQueue.getLong("sends", 0L);

            subQueues.add(new SubQueue(name, (int) weight, (int) maxSends));
            QueuePlugin.debug("Added new subqueue with name " + name + ".");

            if (weight == 0)
                hasRegularQueue = true;
        }

        if (!hasRegularQueue)
            subQueues.add(new SubQueue("regular", 0, 1));

        return true;
    }

    public boolean reload() {
        if (!load())
            return false;

        for (QueuedPlayer player : plugin.queuedPlayers())
            player.recalculatePriority();

        return true;
    }

    private void saveDefaultConfig() {
        if (Files.exists(configPath))
            return;

        try {
            Files.createDirectory(dataFolder);
        } catch (IOException ignored) {}

        try (InputStream is = QueueConfig.class.getResourceAsStream("/" + CONFIG_FILE_NAME)) {
            if (is == null) {
                plugin.logger().warn("Could not find file " + CONFIG_FILE_NAME + " in the plugin jar.");
                return;
            }

            Files.copy(is, configPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Set<SubQueue> newSubQueues() {
        return new ConcurrentSkipListSet<>(this.subQueues);
    }

    public Set<Priority> priorities() {
        return priorities;
    }

    public AutoQueueSettings autoQueueSettings() {
        return autoQueueSettings;
    }

    public record AutoQueueSettings(long delay, String defaultTarget, String autoQueueServer) {}
}

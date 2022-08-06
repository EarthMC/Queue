package net.earthmc.queue.config;

import com.moandjiezana.toml.Toml;
import net.earthmc.queue.Priority;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.earthmc.queue.SubQueue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class QueueConfig {
    private final QueuePlugin plugin;
    private final Path dataFolder;
    private final Path configPath;
    private Toml config;
    private List<Priority> priorities;
    private List<SubQueue> subQueues;
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
        priorities = new ArrayList<>();
        subQueues = new ArrayList<>();

        plugin.setDebug(config.getBoolean("debug", false));

        Toml autoQueueConfig = config.getTable("autoqueue");
        this.autoQueueSettings = new AutoQueueSettings(
                autoQueueConfig.getLong("delay"),
                autoQueueConfig.getString("default-target"),
                new HashSet<>(Arrays.asList(autoQueueConfig.getString("autoqueue-server").toLowerCase(Locale.ROOT).split(",")))
        );

        for (Toml priority : config.getTables("priority")) {
            String name = priority.getString("name", "none");
            long weight = priority.getLong("weight", 0L);
            Component message = MiniMessage.miniMessage().deserialize(priority.getString("message", ""));

            priorities.add(new Priority(name, Math.max((int) weight, 0), message));
            QueuePlugin.debug("Added new priority with name " + name + ".");
        }

        Collections.sort(priorities);

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

        Collections.sort(subQueues);

        Map<SubQueue, Integer> ratios = new HashMap<>();
        for (SubQueue subQueue : this.subQueues)
            ratios.put(subQueue, subQueue.maxSends);

        for (Queue queue : plugin.queues().values())
            queue.getSubQueueRatio().updateOptions(ratios);

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

    public List<SubQueue> newSubQueues() {
        List<SubQueue> newSubQueues = new ArrayList<>();

        for (SubQueue subQueue : this.subQueues)
            newSubQueues.add(new SubQueue(subQueue.name(), subQueue.weight(), subQueue.maxSends()));

        Collections.sort(newSubQueues);

        return newSubQueues;
    }

    public List<Priority> priorities() {
        return priorities;
    }

    public AutoQueueSettings autoQueueSettings() {
        return autoQueueSettings;
    }

    public record AutoQueueSettings(long delay, String defaultTarget, Set<String> autoQueueServers) {}

    public String getStorageType() {
        return config.getString("database.type");
    }

    public String getDatabaseHost() {
        return config.getString("database.host");
    }

    public String getDatabasePort() {
        return config.getString("database.port");
    }

    public String getDatabaseUsername() {
        return config.getString("database.username");
    }

    public String getDatabaseName() {
        return config.getString("database.database_name");
    }

    public String getDatabasePassword() {
        return config.getString("database.password");
    }

    public String getDatabaseFlags() {
        return config.getString("database.flags");
    }
}

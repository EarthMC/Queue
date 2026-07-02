package net.earthmc.queue.config;

import com.moandjiezana.toml.Toml;
import net.earthmc.queue.Priority;
import net.earthmc.queue.QueuePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class QueueConfig {
    private static final String CONFIG_FILE_NAME = "config.toml";

    private final QueuePlugin plugin;
    private final Path dataFolder;
    private final Path configPath;
    private Toml config;

    private Map<String, Priority> priorities;
    private List<SubQueueTemplate> subQueues;
    private List<String> subQueueNames = List.of();
    private AutoQueueSettings autoQueueSettings;

    public QueueConfig(QueuePlugin plugin, Path pluginFolder) {
        this.plugin = plugin;
        this.dataFolder = pluginFolder;
        this.configPath = pluginFolder.resolve(CONFIG_FILE_NAME);
    }

    public boolean load() {
        saveDefaultConfig();

        config = new Toml().read(configPath.toFile());
        priorities = new LinkedHashMap<>();
        subQueues = new ArrayList<>();

        plugin.setDebug(config.getBoolean("debug", false));

        Toml autoQueueConfig = config.getTable("autoqueue");
        this.autoQueueSettings = new AutoQueueSettings(
                autoQueueConfig.getLong("delay"),
                autoQueueConfig.getString("default-target"),
                new HashSet<>(Arrays.asList(autoQueueConfig.getString("autoqueue-server").toLowerCase(Locale.ROOT).split(","))),
                autoQueueConfig.getBoolean("insta-send", false)
        );

        final List<Priority> priorityList = new ArrayList<>();

        for (Toml priority : config.getTables("priority")) {
            String name = priority.getString("name", "none");
            long weight = priority.getLong("weight", 0L);
            Component message = MiniMessage.miniMessage().deserialize(priority.getString("message", ""));

            priorityList.add(new Priority(name, Math.max((int) weight, 0), message));
            QueuePlugin.debug("Added new priority with name " + name + ".");
        }

        Collections.sort(priorityList);
        priorityList.forEach(priority -> priorities.put(priority.name(), priority));

        boolean hasRegularQueue = false;
        for (Toml subQueue : config.getTables("subqueue")) {
            String name = subQueue.getString("name", "regular");
            long weight = subQueue.getLong("min-weight", 0L);
            long maxSends = subQueue.getLong("sends", 0L);

            subQueues.add(new SubQueueTemplate(name, (int) weight, (int) maxSends));
            QueuePlugin.debug("Added new subqueue with name " + name + ".");

            if (weight == 0)
                hasRegularQueue = true;
        }

        if (!hasRegularQueue)
            subQueues.add(new SubQueueTemplate("regular", 0, 1));

        subQueues.sort(Comparator.comparing(SubQueueTemplate::weight, Comparator.reverseOrder()));

        List<String> subQueueNames = new ArrayList<>();
        for (SubQueueTemplate subQueue : this.subQueues) {
            subQueueNames.add(subQueue.name());
        }

        this.subQueueNames = List.copyOf(subQueueNames);

        return true;
    }

    public boolean reload() {
        return load();
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
            plugin.logger().error("while saving default config", e);
        }
    }

    public List<SubQueueTemplate> subQueueTemplates() {
        return List.copyOf(subQueues);
    }

    public @Unmodifiable List<String> subQueueNames() {
        return subQueueNames;
    }

    public Collection<Priority> priorities() {
        return priorities.values();
    }

    public @Nullable Priority priority(final String name) {
        return this.priorities.get(name);
    }

    public AutoQueueSettings autoQueueSettings() {
        return autoQueueSettings;
    }

    public record AutoQueueSettings(long delay, String defaultTarget, Set<String> autoQueueServers, boolean instaSend) {}

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

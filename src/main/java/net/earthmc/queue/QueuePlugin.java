package net.earthmc.queue;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.earthmc.queue.commands.BaseCommand;
import net.earthmc.queue.commands.JoinCommand;
import net.earthmc.queue.commands.LeaveCommand;
import net.earthmc.queue.commands.PauseCommand;
import net.earthmc.queue.commands.QueueCommand;
import net.earthmc.queue.config.QueueConfig;
import net.earthmc.queue.storage.FlatFileStorage;
import net.earthmc.queue.storage.SQLStorage;
import net.earthmc.queue.storage.Storage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(id = "queue", name = "Queue", version = "0.1.2", authors = {"Warriorrr"})
public class QueuePlugin {

    private static QueuePlugin instance;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path pluginFolderPath;
    private final Map<String, Queue> queues = new ConcurrentHashMap<>();
    private final Map<UUID, QueuedPlayer> queuedPlayers = new HashMap<>();
    private QueueConfig config;
    private boolean debug = false;
    private Storage storage;
    private final Map<UUID, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();

    @Inject
    public QueuePlugin(ProxyServer proxy, CommandManager commandManager, Logger logger, @DataDirectory Path pluginFolderPath) {
        QueuePlugin.instance = this;
        this.proxy = proxy;
        this.logger = logger;
        this.pluginFolderPath = pluginFolderPath;

        commandManager.register("joinqueue", new JoinCommand(this));
        commandManager.register("leavequeue", new LeaveCommand());
        commandManager.register("pausequeue", new PauseCommand());
        commandManager.register("queue", new QueueCommand(this));
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.config = new QueueConfig(this, pluginFolderPath);
        this.config.load();

        for (RegisteredServer server : proxy.getAllServers()) {
            queues.put(server.getServerInfo().getName().toLowerCase(Locale.ROOT), new Queue(server, this));
        }

        this.storage = config.getStorageType().equalsIgnoreCase("sql")
                ? new SQLStorage(this)
                : new FlatFileStorage(this, pluginFolderPath.resolve("data"));

        try {
            this.storage.enable();
        } catch (Exception e) {
            logger.error("An exception occurred when enabling the storage, falling back to flatfile storage.", e);
            this.storage = new FlatFileStorage(this, pluginFolderPath.resolve("data"));
        }

        // Load any paused queues from the paused-queues.json file.
        loadPausedQueues();

        proxy.getScheduler().buildTask(this, () -> {
            for (Queue queue : queues().values())
                queue.sendNext();
        }).repeat(500, TimeUnit.MILLISECONDS).schedule();

        proxy.getScheduler().buildTask(this, () -> {
            for (Queue queue : queues.values())
                queue.refreshMaxPlayers();
        }).repeat(10, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.storage != null) {
            try {
                this.storage.disable();
            } catch (Exception e) {
                logger.error("An exception occurred when disabling the storage", e);
            }
        }

        savePausedQueues();
    }

    public boolean reload() {
        if (!this.config.reload())
            return false;

        // Disable storage if it isn't null
        if (this.storage != null) {
            try {
                this.storage.disable();
            } catch (Exception e) {
                logger.error("An exception occurred when disabling the storage.", e);
            }
        }

        this.storage = config.getStorageType().equalsIgnoreCase("sql")
                ? new SQLStorage(this)
                : new FlatFileStorage(this, pluginFolderPath.resolve("data"));

        try {
            this.storage.enable();
        } catch (Exception e) {
            logger.error("An exception occurred when enabling the storage", e);
            this.storage = new FlatFileStorage(this, pluginFolderPath.resolve("data"));
        }

        return true;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        // Load saved data for this player async upon login.
        queued(event.getPlayer()).loadData();
    }

    @Subscribe
    public void onPlayerLeave(DisconnectEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();

        QueuedPlayer player = queuedPlayers.get(uuid);
        if (player != null) {
            if (player.isInQueue())
                player.queue().remove(player);

            event.getPlayer().getCurrentServer().ifPresent(server -> {
                // Set the player's last joined server if it isn't an auto queue server
                if (!config.autoQueueSettings().autoQueueServers().contains(server.getServerInfo().getName().toLowerCase(Locale.ROOT)))
                    player.setLastJoinedServer(server.getServerInfo().getName());
            });

            this.storage.savePlayer(player);
        }

        queuedPlayers.remove(uuid);
        removeAutoQueue(event.getPlayer());
    }

    @Subscribe
    public void onServerConnect(ServerConnectedEvent event) {
        QueuedPlayer player = queued(event.getPlayer());

        // Remove the player from their queue if their queue is for the server they just joined.
        if (player.isInQueue() && player.queue().getServer().getServerInfo().getName().equalsIgnoreCase(event.getServer().getServerInfo().getName()))
            player.queue().remove(player);

        processAutoQueue(event, player);
    }

    @Subscribe(order = PostOrder.LATE)
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (!config.autoQueueSettings().instaSend())
            return;

        if (scheduledTasks.containsKey(event.getPlayer().getUniqueId()) || event.getPlayer().getPermissionValue("queue.autoqueue") == Tristate.FALSE)
            return;

        QueuedPlayer player = queued(event.getPlayer());
        if (player.loadFuture() != null && player.getLastJoinedServer().isEmpty())
            player.loadFuture().join(); // We want to ensure that player data is loaded so that we can get their last server

        final String target = validateAutoQueueTarget(event.getPlayer(), player.getLastJoinedServer().orElse(config.autoQueueSettings().defaultTarget()));

        if (!BaseCommand.hasPrefixedPermission(event.getPlayer(), "queue.join.", target))
            return;

        Queue queue = queue(target);
        if (queue == null || queue.paused() || queue.getServer().getPlayersConnected().size() >= queue.maxPlayers())
            return;

        event.setInitialServer(queue.getServer());
        logger.info(event.getPlayer().getUsername() + " has been sent to " + queue.getServerFormatted() + " via autoqueue.");
    }

    public void processAutoQueue(ServerConnectedEvent event, QueuedPlayer player) {
        final UUID uuid = event.getPlayer().getUniqueId();

        if (
                scheduledTasks.containsKey(uuid) // There's already a scheduled auto queue task for this player
                || event.getPlayer().getPermissionValue("queue.autoqueue") == Tristate.FALSE // The player has the auto queue permission explicitly set to false
                || !config.autoQueueSettings().autoQueueServers().contains(event.getServer().getServerInfo().getName().toLowerCase(Locale.ROOT)) // The player isn't on one of the auto queue servers.
        )
            return;

        if (player.isAutoQueueDisabled()) {
            player.sendMessage(Component.text("Auto queue is currently disabled, use /joinqueue " + player.getLastJoinedServer().orElse(config.autoQueueSettings().defaultTarget()) + " to manually join or /queue auto to re-enable auto queue.", NamedTextColor.GRAY));
            return;
        }

        scheduledTasks.put(uuid, proxy().getScheduler().buildTask(this, () -> {
            scheduledTasks.remove(uuid);

            String target = player.getLastJoinedServer().orElse(config.autoQueueSettings().defaultTarget());
            final String currentServerName = event.getPlayer().getCurrentServer().map(server -> server.getServerInfo().getName()).orElse("unknown");

            target = validateAutoQueueTarget(event.getPlayer(), target);

            // Prevent the player from being auto queued to the server they are already on
            if (target.equalsIgnoreCase(currentServerName))
                return;

            // Simply return if the player doesn't have permissions to join the default target.
            if (!BaseCommand.hasPrefixedPermission(event.getPlayer(), "queue.join.", target))
                return;

            Queue queue = queue(target);
            if (queue != null) {
                debug(event.getPlayer().getUsername() + " has been automatically queued for " + target + ".");
                event.getPlayer().sendMessage(Component.text("You are being automatically queued for " + queue.getServerFormatted() + ".", NamedTextColor.GREEN));
                queue.enqueue(player);
            }
        }).delay(config.autoQueueSettings().delay(), TimeUnit.SECONDS).schedule());
    }

    private String validateAutoQueueTarget(Player player, String target) {
        // Validate that the target is known to the proxy, it isn't an auto queue server, and the player has permissions to join it, otherwise just return the default target.
        return proxy.getServer(target).map(server -> server.getServerInfo().getName())
                .filter(name -> !config.autoQueueSettings().autoQueueServers().contains(name.toLowerCase(Locale.ROOT)))
                .filter(name -> BaseCommand.hasPrefixedPermission(player, "queue.join.", name))
                .orElse(config.autoQueueSettings().defaultTarget());
    }

    public void removeAutoQueue(Player player) {
        ScheduledTask task = scheduledTasks.remove(player.getUniqueId());
        if (task != null)
            task.cancel();
    }

    public Map<String, Queue> queues() {
        return queues;
    }

    public static QueuePlugin instance() {
        return instance;
    }

    public ProxyServer proxy() {
        return this.proxy;
    }

    @Nullable
    public Queue queue(String serverName) {
        Queue queue = queues.get(serverName.toLowerCase(Locale.ROOT));

        if (queue != null)
            return queue;

        // A queue with this name doesn't exist yet, create a new one if a server exists with its name
        Optional<RegisteredServer> registeredServer = proxy.getServer(serverName);
        if (registeredServer.isEmpty())
            return null;

        queue = new Queue(registeredServer.get(), this);
        queues.put(serverName.toLowerCase(Locale.ROOT), queue);

        return queue;
    }

    public QueuedPlayer queued(Player player) {
        return queuedPlayers.computeIfAbsent(player.getUniqueId(), k -> new QueuedPlayer(player));
    }

    public Collection<QueuedPlayer> queuedPlayers() {
        return queuedPlayers.values();
    }

    public static void debug(Object message) {
        if (instance != null && instance.debug)
            instance.logger.info(String.valueOf(message));
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public Logger logger() {
        return this.logger;
    }

    public QueueConfig config() {
        return this.config;
    }

    public Storage storage() {
        return storage;
    }

    public void loadPausedQueues() {
        Path pausedQueuesPath = pluginFolderPath.resolve("paused-queues.json");

        if (Files.exists(pausedQueuesPath)) {
            @SuppressWarnings("UnstableApiUsage")
            Type type = new TypeToken<Map<String, Long>>() {}.getType();

            try {
                Map<String, Long> pausedQueues = new Gson().fromJson(Files.readString(pausedQueuesPath), type);
                for (Map.Entry<String, Long> entry : pausedQueues.entrySet()) {
                    Queue queue = queue(entry.getKey());
                    if (queue == null)
                        continue;

                    Instant instant = Instant.ofEpochSecond(entry.getValue());
                    if (Instant.now().isAfter(instant))
                        continue;

                    queue.pause(true, Instant.ofEpochSecond(entry.getValue()));
                    logger.info("Re-paused the queue for " + entry.getKey() + ".");
                }

                try {
                    Files.deleteIfExists(pausedQueuesPath);
                } catch (IOException e) {
                    logger.error("Failed to delete paused-queues.json", e);
                }
            } catch (Exception ignored) {}
        }
    }

    public void savePausedQueues() {
        Map<String, Long> pausedQueues = new HashMap<>();
        for (Map.Entry<String, Queue> entry : this.queues().entrySet()) {
            if (entry.getValue().paused())
                pausedQueues.put(entry.getKey(), entry.getValue().unpauseTime().getEpochSecond());
        }

        if (pausedQueues.size() > 0) {
            Path pausedQueuesPath = pluginFolderPath.resolve("paused-queues.json");

            try {
                if (!Files.exists(pausedQueuesPath))
                    Files.createFile(pausedQueuesPath);

                Files.writeString(pausedQueuesPath, new Gson().toJson(pausedQueues));

                logger.info("Successfully saved " + pausedQueues.size() + " paused queue(s) to paused-queues.json");
            } catch (Exception e) {
                logger.error("Unable to save " + pausedQueues.size() + " paused queues.", e);
            }
        }
    }
}

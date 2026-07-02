package net.earthmc.queue;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.earthmc.queue.commands.Brig;
import net.earthmc.queue.commands.JoinCommand;
import net.earthmc.queue.commands.LeaveCommand;
import net.earthmc.queue.commands.PauseCommand;
import net.earthmc.queue.commands.QueueCommand;
import net.earthmc.queue.config.QueueConfig;
import net.earthmc.queue.impl.local.LocalQueueController;
import net.earthmc.queue.impl.mycelium.RemoteQueueController;
import net.earthmc.queue.storage.FlatFileStorage;
import net.earthmc.queue.storage.SQLStorage;
import net.earthmc.queue.storage.Storage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(id = "queue", name = "Queue", version = BuildConstants.VERSION, authors = "Warriorrr", dependencies = @Dependency(id = "mycelium", optional = true))
public class QueuePlugin {

    private static QueuePlugin instance;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path pluginFolderPath;
    private final Map<String, Queue> queues = new ConcurrentHashMap<>();
    private QueueConfig config;
    private boolean debug = false;
    private Storage storage;
    private final Map<UUID, ScheduledTask> autoAddToQueueTasks = new ConcurrentHashMap<>();

    private final Map<UUID, CompletableFuture<PlayerData>> playerData = new ConcurrentHashMap<>();
    private QueueController controller;

    @Inject
    public QueuePlugin(ProxyServer proxy, CommandManager commandManager, Logger logger, @DataDirectory Path pluginFolderPath) {
        QueuePlugin.instance = this;
        this.proxy = proxy;
        this.logger = logger;
        this.pluginFolderPath = pluginFolderPath;

        commandManager.register(buildMeta("joinqueue"), JoinCommand.createCommand(this));
        commandManager.register(buildMeta("leavequeue"), LeaveCommand.createCommand(this));
        commandManager.register(buildMeta("pausequeue"), PauseCommand.createCommand(this));
        commandManager.register(buildMeta("queue"), QueueCommand.createCommand(this));
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.config = new QueueConfig(this, pluginFolderPath);
        this.config.load();

        this.storage = config.getStorageType().equalsIgnoreCase("sql")
                ? new SQLStorage(this)
                : new FlatFileStorage(this, pluginFolderPath.resolve("data"));

        try {
            this.storage.enable();
        } catch (Exception e) {
            logger.error("An exception occurred when enabling the storage, falling back to flatfile storage.", e);
            this.storage = new FlatFileStorage(this, pluginFolderPath.resolve("data"));
        }

        if (proxy.getPluginManager().isLoaded("mycelium")) {
            controller = new RemoteQueueController(this);
        } else {
            controller = new LocalQueueController(this);
        }

        proxy.getEventManager().register(this, controller);

        // Load any paused queues from the paused-queues.json file.
        loadPausedQueues();

        proxy.getScheduler().buildTask(this, () -> {
            if (controller.canTickQueues()) {
                for (Queue queue : queues().values()) {
                    queue.sendNext();
                }
            }
        }).repeat(500, TimeUnit.MILLISECONDS).schedule();

        proxy.getScheduler().buildTask(this, () -> {
            for (Queue queue : queues.values()) {
                queue.refreshMaxPlayers();
            }
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
        controller.disable();
    }

    public boolean reload() {
        if (!this.config.reload())
            return false;

        this.controller.reloadCallback();

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
        playerData.put(event.getPlayer().getUniqueId(), this.storage.loadPlayer(event.getPlayer().getUniqueId()));
    }

    @Subscribe
    public void onPlayerLeave(DisconnectEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();

        final CompletableFuture<PlayerData> playerDataFuture = this.playerData.remove(uuid);
        if (playerDataFuture != null && playerDataFuture.isDone()) {
            final PlayerData playerData = playerDataFuture.join();

            event.getPlayer().getCurrentServer().ifPresent(server -> {
                // Set the player's last joined server if it isn't an auto queue server
                if (!config.autoQueueSettings().autoQueueServers().contains(server.getServerInfo().getName().toLowerCase(Locale.ROOT))) {
                    playerData.setLastJoinedServer(server.getServerInfo().getName());
                }
            });

            if (playerData.isDirty()) {
                this.storage.savePlayer(uuid, playerData);
            }
        }

        cancelAutoQueueTask(event.getPlayer());
    }

    @Subscribe
    public void onServerConnect(ServerConnectedEvent event) {
        QueuedPlayer player = queued(event.getPlayer());

        // Remove the player from their queue if their queue is for the server they just joined.
        final Queue queue = player.queue();
        if (queue != null && queue.getServer().getServerInfo().getName().equalsIgnoreCase(event.getServer().getServerInfo().getName()))
            queue.remove(player);

        processAutoQueue(event, player);
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        final RegisteredServer initial = event.getInitialServer().orElse(null);

        if (
                !config.autoQueueSettings().instaSend()
                || autoAddToQueueTasks.containsKey(event.getPlayer().getUniqueId())
                || (initial != null && !config.autoQueueSettings().autoQueueServers().contains(initial.getServerInfo().getName().toLowerCase(Locale.ROOT)))
                || event.getPlayer().getPermissionValue("queue.autoqueue") == Tristate.FALSE
        )
            return;

        final CompletableFuture<PlayerData> loadFuture = playerData.get(event.getPlayer().getUniqueId());
        final PlayerData data = loadFuture != null ? loadFuture.join() : null;

        final String target = validateAutoQueueTarget(event.getPlayer(), Optional.ofNullable(data).flatMap(PlayerData::getLastJoinedServer).orElse(config.autoQueueSettings().defaultTarget()));

        if (!Brig.hasPrefixedPermission(event.getPlayer(), "queue.join.", target))
            return;

        final Queue queue = queue(target);
        final int playerCount;
        if (queue == null || queue.paused() || (playerCount = queue.playerCount()) > 1 || queue.connectedPlayerCount() + playerCount >= queue.maxPlayers()) {
            return;
        }

        event.setInitialServer(queue.getServer());
        logger.info("{} has been sent to {} via autoqueue.", event.getPlayer().getUsername(), queue.getServerFormatted());
    }

    public void processAutoQueue(ServerConnectedEvent event, QueuedPlayer player) {
        final UUID uuid = event.getPlayer().getUniqueId();

        if (
                autoAddToQueueTasks.containsKey(uuid) // There's already a scheduled auto queue task for this player
                || event.getPlayer().getPermissionValue("queue.autoqueue") == Tristate.FALSE // The player has the auto queue permission explicitly set to false
                || !config.autoQueueSettings().autoQueueServers().contains(event.getServer().getServerInfo().getName().toLowerCase(Locale.ROOT)) // The player isn't on one of the auto queue servers.
        )
            return;

        final CompletableFuture<PlayerData> loadFuture = playerData.get(event.getPlayer().getUniqueId());
        final PlayerData data = loadFuture != null ? loadFuture.join() : null;

        if (data != null && data.isAutoQueueDisabled()) {
            final String target = validateAutoQueueTarget(event.getPlayer(), data.getLastJoinedServer().orElse(config.autoQueueSettings().defaultTarget()));

            player.sendMessage(Component.text("Auto queue is currently disabled, use ", NamedTextColor.GRAY)
                .append(Component.text("/joinqueue " + target).clickEvent(ClickEvent.runCommand("/joinqueue " + target)))
                .append(Component.text(" to manually join or "))
                .append(Component.text("/queue auto").clickEvent(ClickEvent.runCommand("/queue auto")))
                .append(Component.text(" to re-enable auto queue.")));
            return;
        }

        autoAddToQueueTasks.put(uuid, proxy().getScheduler().buildTask(this, () -> {
            autoAddToQueueTasks.remove(uuid);

            String target = Optional.ofNullable(data).flatMap(PlayerData::getLastJoinedServer).orElse(config.autoQueueSettings().defaultTarget());
            final String currentServerName = event.getPlayer().getCurrentServer().map(server -> server.getServerInfo().getName()).orElse("unknown");

            target = validateAutoQueueTarget(event.getPlayer(), target);

            // Prevent the player from being auto queued to the server they are already on
            if (target.equalsIgnoreCase(currentServerName))
                return;

            // Simply return if the player doesn't have permissions to join the default target.
            if (!Brig.hasPrefixedPermission(event.getPlayer(), "queue.join.", target))
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
                .filter(name -> config.autoQueueSettings().autoQueueServers().contains(name.toLowerCase(Locale.ROOT)))
                .filter(name -> Brig.hasPrefixedPermission(player, "queue.join.", name))
                .orElse(config.autoQueueSettings().defaultTarget());
    }

    public void cancelAutoQueueTask(Player player) {
        ScheduledTask task = autoAddToQueueTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
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

        queue = controller.createQueue(registeredServer.get());
        queues.put(serverName.toLowerCase(Locale.ROOT), queue);

        return queue;
    }

    public QueuedPlayer queued(Player player) {
        return controller.player(player);
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
            Type type = new TypeToken<Set<PausedQueue>>(){}.getType();

            try {
                Set<PausedQueue> pausedQueues = new Gson().fromJson(Files.readString(pausedQueuesPath), type);
                for (final PausedQueue pausedQueue : pausedQueues) {
                    Queue queue = queue(pausedQueue.server());
                    if (queue == null)
                        continue;

                    final Instant unpauseTime = Instant.ofEpochSecond(pausedQueue.unpauseTime);

                    if (Instant.now().isAfter(unpauseTime))
                        continue;

                    queue.pause(unpauseTime, pausedQueue.reason());
                    logger.info("Re-paused the queue for {}.", pausedQueue.server());
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
        Set<PausedQueue> pausedQueues = new HashSet<>();
        for (Map.Entry<String, Queue> entry : this.queues().entrySet()) {
            if (entry.getValue().paused()) {
                pausedQueues.add(new PausedQueue(entry.getKey(), entry.getValue().unpauseTime().getEpochSecond(), entry.getValue().pauseReason()));
            }
        }

        if (!pausedQueues.isEmpty()) {
            Path pausedQueuesPath = pluginFolderPath.resolve("paused-queues.json");

            try {
                if (!Files.exists(pausedQueuesPath))
                    Files.createFile(pausedQueuesPath);

                Type type = new TypeToken<Set<PausedQueue>>(){}.getType();
                Files.writeString(pausedQueuesPath, new Gson().toJson(pausedQueues, type));

                logger.info("Successfully saved {} paused queue(s) to paused-queues.json", pausedQueues.size());
            } catch (Exception e) {
                logger.error("Unable to save {} paused queues.", pausedQueues.size(), e);
            }
        }
    }

    private record PausedQueue(String server, long unpauseTime, String reason) {}

    private CommandMeta buildMeta(String alias) {
        return this.proxy.getCommandManager().metaBuilder(alias).plugin(this).build();
    }

    public QueueController controller() {
        return controller;
    }

    public Map<UUID, CompletableFuture<PlayerData>> getPlayerData() {
        return playerData;
    }
}

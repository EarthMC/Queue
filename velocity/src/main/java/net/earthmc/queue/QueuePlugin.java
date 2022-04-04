package net.earthmc.queue;

import com.google.common.io.ByteArrayDataInput;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.earthmc.queue.commands.JoinCommand;
import net.earthmc.queue.commands.LeaveCommand;
import net.earthmc.queue.commands.PauseCommand;
import net.earthmc.queue.commands.QueueCommand;
import net.earthmc.queue.commands.QueuePluginCommand;
import net.earthmc.queue.config.QueueConfig;
import net.earthmc.queue.storage.FlatFileStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(id = "queue", name = "Queue", version = "0.0.1", authors = {"Warriorrr"})
public class QueuePlugin {

    private static ProxyServer proxy;
    private static QueuePlugin instance;
    private final Logger logger;
    private final Map<String, Queue> queues = new HashMap<>();
    private final Map<UUID, QueuedPlayer> queuedPlayers = new HashMap<>();
    private final QueueConfig config;
    private boolean debug = false;
    private final FlatFileStorage storage;
    private final Map<UUID, ScheduledTask> scheduledTasks = new HashMap<>();

    @Inject
    public QueuePlugin(ProxyServer proxy, CommandManager commandManager, Logger logger, @DataDirectory Path dataFolderPath) {
        QueuePlugin.instance = this;
        QueuePlugin.proxy = proxy;
        this.logger = logger;
        this.config = new QueueConfig(this, dataFolderPath);
        this.config.load();

        this.storage = new FlatFileStorage(this, dataFolderPath.resolve("data"));

        commandManager.register("joinqueue", new JoinCommand());
        commandManager.register("leavequeue", new LeaveCommand());
        commandManager.register("pausequeue", new PauseCommand());
        commandManager.register("queue", new QueueCommand());
        commandManager.register("qp", new QueuePluginCommand(this));
        proxy.getChannelRegistrar().register(MinecraftChannelIdentifier.create("queue", "join"));
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        for (RegisteredServer server : proxy.getAllServers()) {
            queues.put(server.getServerInfo().getName().toLowerCase(), new Queue(server, this));
        }

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
    public void onPlayerLeave(DisconnectEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();

        if (!queuedPlayers.containsKey(uuid))
            return;

        QueuedPlayer player = queuedPlayers.get(uuid);
        if (player != null) {
            if (player.isInQueue())
                player.queue().remove(player);

            event.getPlayer().getCurrentServer().ifPresent(server -> {
                if (!server.getServerInfo().getName().equalsIgnoreCase(config.autoQueueSettings().autoQueueServer()))
                    player.setLastJoinedServer(server.getServerInfo().getName());
            });

            this.storage.savePlayer(player);
        }

        queuedPlayers.remove(uuid);
    }

    @Subscribe
    public void onPlayerJoin(ServerConnectedEvent event) {
        QueuedPlayer player = queued(event.getPlayer());

        // Remove the player from their queue if their queue is for the server they just joined.
        if (player.isInQueue() && player.queue().getServer().getServerInfo().getName().equalsIgnoreCase(event.getServer().getServerInfo().getName()))
            player.queue().remove(player);

        processAutoQueue(event, player);
    }

    public void processAutoQueue(ServerConnectedEvent event, QueuedPlayer player) {
        if (player.isAutoQueueDisabled())
            return;

        if (event.getPlayer().getPermissionValue("queue.autoqueue") == Tristate.FALSE)
            return;

        if (!event.getServer().getServerInfo().getName().equalsIgnoreCase(config.autoQueueSettings().autoQueueServer()))
            return;

        final UUID uuid = event.getPlayer().getUniqueId();

        if (scheduledTasks.containsKey(uuid))
            return;

        scheduledTasks.put(uuid, proxy().getScheduler().buildTask(this, () -> {
            scheduledTasks.remove(uuid);

            player.loadData().thenRunAsync(() -> {
                String target = player.getLastJoinedServer().orElse(config.autoQueueSettings().defaultTarget());
                final String currentServerName = event.getPlayer().getCurrentServer().map(server -> server.getServerInfo().getName()).orElse("unknown");

                // Validate that the target is known to the proxy
                target = proxy.getServer(target).map(server -> server.getServerInfo().getName())
                        .filter(name -> !name.equalsIgnoreCase(config.autoQueueSettings().autoQueueServer()))
                        .orElse(config.autoQueueSettings().defaultTarget());

                // Prevent the player from being auto queued to the server they are already on
                if (target.equalsIgnoreCase(currentServerName))
                    return;

                Queue queue = queue(target);
                if (queue != null) {
                    debug(event.getPlayer().getUsername() + " has been automatically queued for " + target + ".");
                    event.getPlayer().sendMessage(Component.text("You are being automatically queued for " + queue.getServerFormatted() + ".", NamedTextColor.GREEN));
                    queue.enqueue(player);
                }
            });
        }).delay(config.autoQueueSettings().delay(), TimeUnit.SECONDS).schedule());
    }

    public void removeAutoQueue(Player player) {
        ScheduledTask task = scheduledTasks.get(player.getUniqueId());
        if (task != null)
            task.cancel();

        scheduledTasks.remove(player.getUniqueId());
    }

    @Subscribe
    public void onPlayerKick(KickedFromServerEvent event) {
        Component reason = event.getServerKickReason().orElse(Component.text("You have been kicked from the server you were on.", NamedTextColor.RED));
        event.setResult(KickedFromServerEvent.DisconnectPlayer.create(reason));
    }

    @Subscribe
    public void onPluginMessageReceived(PluginMessageEvent event) {
        if (!(event.getSource() instanceof ServerConnection))
            return;

        if (event.getIdentifier().getId().equals("queue:join")) {
            ByteArrayDataInput input = event.dataAsDataStream();
            String name = input.readUTF();
            String target = input.readUTF();

            proxy.getPlayer(name).ifPresent(player -> proxy.getCommandManager().executeAsync(player, "joinqueue " + target + " confirm"));
        }
    }

    @Subscribe
    @SuppressWarnings("UnstableApiUsage")
    public void onCommandsReceive(PlayerAvailableCommandsEvent event) {
        // Hide this command from players in order to prevent confusion in tab completions
        event.getRootNode().removeChildByName("queuejoin");
        event.getRootNode().removeChildByName("queuebukkit:queuejoin");
    }

    public Map<String, Queue> queues() {
        return queues;
    }

    public static QueuePlugin instance() {
        return instance;
    }

    public static ProxyServer proxy() {
        return proxy;
    }

    @Nullable
    public Queue queue(String serverName) {
        Queue queue = queues.get(serverName.toLowerCase());

        if (queue != null)
            return queue;

        Optional<RegisteredServer> registeredServer = proxy.getServer(serverName);
        if (registeredServer.isEmpty())
            return null;

        queue = new Queue(registeredServer.get(), this);
        queues.put(serverName.toLowerCase(), queue);

        return queue;
    }

    public QueuedPlayer queued(Player player) {
        queuedPlayers.computeIfAbsent(player.getUniqueId(), k -> new QueuedPlayer(player));

        return queuedPlayers.get(player.getUniqueId());
    }

    public Collection<QueuedPlayer> queuedPlayers() {
        return queuedPlayers.values();
    }

    public static void log(Object message) {
        instance.logger.info(String.valueOf(message));
    }

    public static void warn(Object message) {
        instance.logger.warn(String.valueOf(message));
    }

    public static void debug(Object message) {
        if (instance.debug)
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

    public FlatFileStorage getStorage() {
        return storage;
    }
}

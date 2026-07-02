package net.earthmc.queue.impl.local;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueueController;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LocalQueueController extends QueueController {
    private final QueuePlugin plugin;
    private final Map<UUID, LocalQueuedPlayer> queuedPlayers = new HashMap<>();

    public LocalQueueController(QueuePlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPlayerDisconnect(final DisconnectEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        this.queuedPlayers.remove(uuid);
    }

    @Override
    public Queue createQueue(RegisteredServer server) {
        return new LocalQueue(server, plugin);
    }

    @Override
    public QueuedPlayer player(Player player) {
        return queuedPlayers.computeIfAbsent(player.getUniqueId(), k -> new LocalQueuedPlayer(player.getUniqueId(), player.getUsername()));
    }

    @Override
    public void reloadCallback() {
        for (final LocalQueuedPlayer player : this.queuedPlayers.values()) {
            player.clearPriority();
        }
    }

    @Override
    public boolean canTickQueues() {
        return true;
    }
}

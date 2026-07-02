package net.earthmc.queue.impl.mycelium;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.mycelium.api.Mycelium;
import net.earthmc.mycelium.api.messaging.ChannelIdentifier;
import net.earthmc.mycelium.api.messaging.Listener;
import net.earthmc.mycelium.api.serialization.Codecs;
import net.earthmc.mycelium.api.store.params.SetOptions;
import net.earthmc.queue.Priority;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueueController;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.earthmc.queue.api.mycelium.PauseQueue;
import net.earthmc.queue.api.mycelium.UnpauseQueue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A remote queue controller that uses a simple leadership algorithm for determining who is responsible for ticking queues.
 * <p>
 * 1. The first instance to claim the key becomes the leader.
 * 2. The leader must confirm it's alive every 30 seconds or risk losing leadership.
 * 3. Other candidates will repeatedly try to take the key if it's available.
 * 4. If the leader willingly abdicates, a signal is sent to all other instances to trigger taking leadership immediately, with one winner.
 */
public class RemoteQueueController extends QueueController {
    private static final Duration LEADER_EXPIRATION = Duration.ofSeconds(30);

    private final QueuePlugin plugin;
    private final Mycelium api = Mycelium.api();
    private final String proxyId = api.platform().id();

    private Instant lastLeaderTest = Instant.MIN;
    private boolean isLeader;

    private final List<Listener> listeners = new ArrayList<>();

    public RemoteQueueController(QueuePlugin plugin) {
        this.plugin = plugin;

        listeners.add(api.messaging().registerChannel(api.messaging().bind(ChannelIdentifier.identifier("queue:abdicate"), Codecs.STRING), incoming -> tryBecomeLeader()));

        listeners.add(api.messaging().registerChannel(api.messaging().bind(ChannelIdentifier.identifier("queue:wakeup"), Codecs.STRING), incoming -> {
            if (!isLeader()) {
                return;
            }

            final Queue queue = plugin.queue(incoming.data());
            if (queue != null) {
                queue.wakeup();
            }
        }));

        // each instance registers a listener for this, but it's fine if multiple instances do the same thing
        listeners.add(api.messaging().registerChannel(api.messaging().bind(ChannelIdentifier.identifier("queue:pause"), PauseQueue.CODEC), incoming -> {
            final Queue queue = plugin.queue(incoming.data().serverName());
            if (queue == null) {
                return;
            }

            queue.pause(incoming.data().unpauseTime(), incoming.data().reason());
        }));

        listeners.add(api.messaging().registerChannel(api.messaging().bind(ChannelIdentifier.identifier("queue:unpause"), UnpauseQueue.CODEC), incoming -> {
            final Queue queue = plugin.queue(incoming.data().serverName());
            if (queue != null) {
                queue.unpause();
            }
        }));

        plugin.proxy().getScheduler().buildTask(plugin, () -> {
            // extend leader deadline and at the same time confirm that this instance is still the leader.
            if (this.isLeader && !api.dataStore().set("queue:leader", Codecs.STRING, proxyId, SetOptions.setOptions().expiration(LEADER_EXPIRATION).valueEq(Codecs.STRING, proxyId))) {
                this.isLeader = false;
            }
        }).delay(Duration.ZERO).repeat(Duration.ofSeconds(15)).schedule();
    }

    @Override
    public Queue createQueue(RegisteredServer server) {
        return new RemoteQueue(server, plugin);
    }

    @Override
    public QueuedPlayer player(Player player) {
        return new RemoteQueuedPlayer(player.getUniqueId(), player.getUsername());
    }

    @Subscribe
    public void onPlayerConnect(PostLoginEvent event) {
        Priority priority = Priority.NONE_PRIORITY;

        for (Priority test : QueuePlugin.instance().config().priorities()) {
            if (event.getPlayer().hasPermission("queue.priority." + test.name().toLowerCase(Locale.ROOT))) {
                priority = test;
                break;
            }
        }

        api.dataStore().hset("queue:player:" + event.getPlayer().getUniqueId(), "priority", Codecs.STRING, priority.name()); // TODO: need to deal with cleaning this up if this proxy crashes
        api.dataStore().hset("queue:player:" + event.getPlayer().getUniqueId(), "proxy", Codecs.STRING, api.platform().id());
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        final QueuedPlayer player = player(event.getPlayer());
        final Queue queue = player.queue();
        if (queue != null) {
            queue.remove(player);
        }

        final String hashKey = "queue:player:" + event.getPlayer().getUniqueId();
        api.dataStore().remove(hashKey);
    }

    @Override
    public void disable() {
        this.listeners.forEach(Listener::unregister);
        this.listeners.clear();

        if (this.isLeader) {
            api.dataStore().removeIfEquals("queue:leader", Codecs.STRING, proxyId);
            api.messaging().message(api.messaging().bind(ChannelIdentifier.identifier("queue:abdicate"), Codecs.STRING), proxyId).send();
        }
    }

    @Override
    public boolean canTickQueues() {
        return this.isLeader();
    }

    public boolean isLeader() {
        if (this.isLeader) {
            return true;
        }

        final Instant now = Instant.now();
        if (this.lastLeaderTest.plusSeconds(10).isAfter(now)) {
            return false;
        }

        this.lastLeaderTest = now;

        tryBecomeLeader();
        return this.isLeader;
    }

    private void tryBecomeLeader() {
        if (this.isLeader) {
            return;
        }

        this.isLeader = api.dataStore().set("queue:leader", Codecs.STRING, proxyId, SetOptions.setOptions().nx().expiration(LEADER_EXPIRATION));
        if (this.isLeader) {
            plugin.logger().info("This proxy instance is now the leader!");
        }
    }
}

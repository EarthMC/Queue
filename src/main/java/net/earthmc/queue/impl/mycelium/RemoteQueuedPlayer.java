package net.earthmc.queue.impl.mycelium;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.mycelium.api.Mycelium;
import net.earthmc.mycelium.api.network.Server;
import net.earthmc.mycelium.api.serialization.Codecs;
import net.earthmc.queue.Priority;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.earthmc.queue.object.ConnectionResult;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RemoteQueuedPlayer extends QueuedPlayer implements ForwardingAudience.Single {
    private static final Mycelium api = Mycelium.api();
    private final String hashKey;

    public RemoteQueuedPlayer(UUID uuid, String name) {
        super(uuid, name);
        hashKey = "queue:player:" + uuid;
    }

    @Override
    public Queue queue() {
        final String queueName = api.dataStore().hget(hashKey, "queue", Codecs.STRING);
        if (queueName == null) {
            return null;
        }

        return QueuePlugin.instance().queue(queueName);
    }

    @Override
    public void queue(@Nullable Queue queue) {
        if (queue == null) {
            api.dataStore().hdel(hashKey, "queue");
        } else if (isConnected()) {
            api.dataStore().hset(hashKey, "queue", Codecs.STRING, queue.getName());
        }
    }

    @Override
    public Priority priority() {
        final String priorityName = api.dataStore().hget(hashKey, "priority", Codecs.STRING);
        if (priorityName == null) {
            return Priority.NONE_PRIORITY;
        }

        final Priority priority = QueuePlugin.instance().config().priority(priorityName);
        return Objects.requireNonNullElse(priority, Priority.NONE_PRIORITY);
    }

    @Override
    public boolean isConnected() {
        return player() != null || api.network().hasPlayer(uuid);
    }

    @Override
    public CompletableFuture<? extends ConnectionResult> sendToServer(RegisteredServer server) {
        final net.earthmc.mycelium.api.network.Player mPlayer = api.network().getPlayerByUUID(uuid);
        final Server mServer = api.network().getServerById(server.getServerInfo().getName().toLowerCase(Locale.ROOT));
        if (mPlayer == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (mServer == null) {
            return CompletableFuture.completedFuture(new ConnectionResult.FailedWithMessage(Component.text(server.getServerInfo().getName() + " is currently unreachable/offline", NamedTextColor.RED)));
        }

        final Server currentServer = mPlayer.server();
        if (currentServer != null && currentServer.name().equals(server.getServerInfo().getName())) {
            return CompletableFuture.completedFuture(null);
        }

        return mPlayer.transferToServer(mServer)
            .thenApply(result -> {
                if (result.failureMessage() != null) {
                    return new ConnectionResult.FailedWithMessage(result.failureMessage());
                } else {
                    return new ConnectionResult.Resulted(result.successful());
                }
            });
    }

    @Override
    public @NotNull Audience audience() {
        final Player player = player();
        if (player != null) {
            return player;
        }

        final net.earthmc.mycelium.api.network.Player mPlayer = api.network().getPlayerByUUID(uuid);
        return Objects.requireNonNullElse(mPlayer, Audience.empty());
    }

    private @Nullable Player player() {
        return QueuePlugin.instance().proxy().getPlayer(uuid).orElse(null);
    }
}

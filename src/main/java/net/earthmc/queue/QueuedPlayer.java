package net.earthmc.queue;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.queue.object.ConnectionResult;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class QueuedPlayer implements Audience {
    protected final UUID uuid;
    protected final String name;

    public QueuedPlayer(final UUID uuid, final String name) {
        this.uuid = uuid;
        this.name = name;
    }

    @NotNull
    public UUID uuid() {
        return this.uuid;
    }

    @NotNull
    public String name() {
        return this.name;
    }

    public abstract Queue queue();

    public abstract void queue(@Nullable Queue queue);

    public boolean isInQueue() {
        final Queue queue = queue();
        if (queue != null)
            if (!queue.hasPlayer(this))
                queue(null);

        return queue != null;
    }

    public abstract Priority priority();

    public abstract boolean isConnected();

    public abstract CompletableFuture<? extends @Nullable ConnectionResult> sendToServer(final RegisteredServer server);

    /**
     * Gets the player's current position in their sub queue, or -1 if they are not in a queue.
     * @return -1 or the player's sub queue position
     */
    public int position() {
        final Queue queue = queue();
        if (queue == null)
            return -1;

        return queue.getSubQueue(this).playerPosition(this);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof QueuedPlayer player)) return false;
        return Objects.equals(uuid, player.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }
}

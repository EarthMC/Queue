package net.earthmc.queue;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class QueuedPlayer implements ForwardingAudience.Single {
    private final Player player;
    private Queue queue;
    private Priority priority;
    private String lastJoinedServer;
    private boolean autoQueueDisabled;
    private boolean dataLoaded;
    private final UUID uuid;

    public QueuedPlayer(Player player) {
        this.player = player;
        this.uuid = player.getUniqueId();
    }

    public Player player() {
        return player;
    }

    public Priority priority() {
        if (priority == null)
            priority = calculatePriority();

        return priority;
    }

    public int position() {
        if (queue == null)
            return -1;

        return queue.getSubQueue(this).players.indexOf(this);
    }

    public boolean isInQueue() {
        if (this.queue != null)
            if (!this.queue.hasPlayer(this))
                this.queue = null;

        return this.queue != null;
    }

    public Queue queue() {
        return this.queue;
    }

    public void queue(Queue queue) {
        this.queue = queue;
    }

    private @NotNull Priority calculatePriority() {
        for (Priority priority : QueuePlugin.instance().config().priorities()) {
            if (player.hasPermission("queue.priority." + priority.name().toLowerCase(Locale.ROOT)))
                return priority;
        }

        return new Priority("none", 0, Component.empty());
    }

    public void recalculatePriority() {
        // Recalculate the priority if it's set
        if (this.priority != null)
            this.priority = calculatePriority();
    }

    @Override
    public @NotNull Audience audience() {
        return player;
    }

    public @NotNull UUID uuid() {
        return this.uuid;
    }

    public CompletableFuture<Void> loadData() {
        if (dataLoaded)
            return CompletableFuture.completedFuture(null);

        dataLoaded = true;
        return QueuePlugin.instance().getStorage().loadSavedData(this);
    }

    public boolean isAutoQueueDisabled() {
        return autoQueueDisabled;
    }

    public void setAutoQueueDisabled(boolean autoQueueDisabled) {
        this.autoQueueDisabled = autoQueueDisabled;
    }

    public Optional<String> getLastJoinedServer() {
        return Optional.ofNullable(lastJoinedServer);
    }

    public void setLastJoinedServer(String lastJoinedServer) {
        this.lastJoinedServer = lastJoinedServer;
    }
}

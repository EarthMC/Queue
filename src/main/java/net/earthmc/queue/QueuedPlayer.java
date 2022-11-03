package net.earthmc.queue;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class QueuedPlayer implements ForwardingAudience.Single {
    private static final Priority NONE_PRIORITY = new Priority("none", 0, Component.empty());

    private final UUID uuid;
    private final String name;
    private Queue queue;
    private Priority priority;
    private String lastJoined;
    private boolean autoQueueDisabled;
    private boolean dataLoaded = false;

    public QueuedPlayer(@NotNull Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getUsername();
    }

    @Nullable
    public Player player() {
        return QueuePlugin.instance().proxy().getPlayer(this.uuid).orElse(null);
    }

    /**
     * @return The player's priority, calculating it if required.
     */
    @NotNull
    public Priority priority() {
        if (priority == null)
            priority = calculatePriority();

        return priority;
    }

    /**
     * Gets the player's current position in their sub queue, or -1 if they are not in a queue.
     * @return -1 or the player's sub queue position
     */
    public int position() {
        if (queue == null)
            return -1;

        return queue.getSubQueue(this).players().indexOf(this);
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

    public void queue(@Nullable Queue queue) {
        this.queue = queue;
    }

    private @NotNull Priority calculatePriority() {
        Player player = player();

        if (player == null)
            return NONE_PRIORITY;

        for (Priority priority : QueuePlugin.instance().config().priorities()) {
            if (player.hasPermission("queue.priority." + priority.name().toLowerCase(Locale.ROOT)))
                return priority;
        }

        return NONE_PRIORITY;
    }

    public void recalculatePriority() {
        // Recalculate the priority if it's set
        if (this.priority != null)
            this.priority = calculatePriority();
    }

    @Override
    public @NotNull Audience audience() {
        return QueuePlugin.instance().proxy().getPlayer(this.uuid).map(player -> (Audience) player).orElse(Audience.empty());
    }

    @NotNull
    public UUID uuid() {
        return this.uuid;
    }

    @NotNull
    public String name() {
        return this.name;
    }

    public void loadData() {
        if (!dataLoaded) {
            dataLoaded = true;
            QueuePlugin.instance().storage().loadPlayer(this);
        }
    }

    public boolean isAutoQueueDisabled() {
        return this.autoQueueDisabled;
    }

    public void setAutoQueueDisabled(boolean autoQueueDisabled) {
        this.autoQueueDisabled = autoQueueDisabled;
    }

    public Optional<String> getLastJoinedServer() {
        return Optional.ofNullable(this.lastJoined);
    }

    public void setLastJoinedServer(@Nullable String lastJoinedServer) {
        this.lastJoined = lastJoinedServer;
    }
}

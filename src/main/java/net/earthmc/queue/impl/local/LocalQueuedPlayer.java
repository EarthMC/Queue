package net.earthmc.queue.impl.local;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LocalQueuedPlayer extends QueuedPlayer implements ForwardingAudience.Single {
    private Queue queue;
    private Priority priority;

    public LocalQueuedPlayer(UUID uuid, String name) {
        super(uuid, name);
    }

    @Nullable
    public Player player() {
        return QueuePlugin.instance().proxy().getPlayer(this.uuid).orElse(null);
    }

    @Override
    public @NotNull Audience audience() {
        return QueuePlugin.instance().proxy().getPlayer(this.uuid).map(player -> (Audience) player).orElse(Audience.empty());
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

    @Override
    public boolean isConnected() {
        final Player player = player();
        return player != null && player.isActive();
    }

    @Override
    public CompletableFuture<? extends ConnectionResult> sendToServer(RegisteredServer server) {
        final Player player = player();
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Make sure the server the player is being sent to isn't the one they're currently on
        if (player.getCurrentServer().map(conn -> conn.getServerInfo().getName()).orElse("unknown").equalsIgnoreCase(server.getServerInfo().getName())) {
            return CompletableFuture.completedFuture(null);
        }

        return player.createConnectionRequest(server).connect().thenApply(result -> {
            if (result.isSuccessful()) {
                return new ConnectionResult.Resulted(true);
            } else {
                Component reason = switch (result.getStatus()) {
                    case CONNECTION_IN_PROGRESS -> Component.text("You are already being connected to this server!", NamedTextColor.RED);
                    case SERVER_DISCONNECTED -> result.getReasonComponent().isPresent() ? result.getReasonComponent().get() : Component.text("The target server has refused your connection.", NamedTextColor.RED);
                    case ALREADY_CONNECTED -> Component.text("You are already connected to this server!", NamedTextColor.RED);
                    case CONNECTION_CANCELLED -> Component.text("Your connection has been cancelled unexpectedly.", NamedTextColor.RED);
                    default -> Component.text("", NamedTextColor.RED);
                };

                return new ConnectionResult.FailedWithMessage(reason);
            }
        }).exceptionally(throwable -> new ConnectionResult.Resulted(false));
    }

    @Override
    public Queue queue() {
        return this.queue;
    }

    @Override
    public void queue(@Nullable Queue queue) {
        this.queue = queue;
    }

    private @NotNull Priority calculatePriority() {
        Player player = player();

        if (player == null)
            return Priority.NONE_PRIORITY;

        for (Priority priority : QueuePlugin.instance().config().priorities()) {
            if (player.hasPermission("queue.priority." + priority.name().toLowerCase(Locale.ROOT)))
                return priority;
        }

        return Priority.NONE_PRIORITY;
    }

    public void clearPriority() {
        // Reset the priority to null so that it's re-calculated next time #priority is called.
        this.priority = null;
    }
}

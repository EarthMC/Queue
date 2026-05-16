package net.earthmc.queue;

import com.google.common.collect.Iterables;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.queue.object.Ratio;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.Vector;
import java.util.function.Predicate;

/**
 * Represents a queue for a server.
 */
public abstract class Queue {
    private static final Duration TIME_BETWEEN_SENDS = Duration.ofMillis(500);
    private static final Predicate<SubQueue> NOT_EMPTY_PREDICATE = subQueue -> !subQueue.players().isEmpty();

    private final QueuePlugin plugin;
    private final List<SubQueue> subQueues;
    private final SubQueue regularQueue;
    private final Ratio<SubQueue> subQueueRatio;
    private final RegisteredServer server;
    private final String formattedName;
    private final String name;

    private int maxPlayers;
    private Instant lastSendTime = Instant.EPOCH;
    private int failedAttempts;

    public Queue(RegisteredServer server, QueuePlugin plugin, List<SubQueue> subQueues) {
        this.server = server;
        this.plugin = plugin;

        String name = server.getServerInfo().getName();
        this.name = name.toLowerCase(Locale.ROOT);

        this.formattedName = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);

        refreshMaxPlayers();
        this.subQueues = subQueues;
        this.subQueueRatio = new Ratio<>(this.subQueues);
        this.regularQueue = Iterables.getLast(this.subQueues);
    }

    @VisibleForTesting
    protected Queue(final List<SubQueue> subQueues) {
        this.plugin = null;
        this.server = null;

        this.formattedName = "TestQueue";
        this.name = "testqueue";

        this.subQueues = subQueues;
        this.subQueueRatio = new Ratio<>(this.subQueues);
        this.regularQueue = Iterables.getLast(this.subQueues);
    }

    public void refreshMaxPlayers() {
        server.ping().thenAccept(ping -> ping.getPlayers().ifPresent(players -> this.maxPlayers = players.getMax()));
    }

    public void sendNext() {
        if (!canSend())
            return;

        if (failedAttempts >= 5) {
            pause(Instant.now().plusSeconds(30));
            for (QueuedPlayer player : allPlayers()) {
                player.sendMessage(Component.text("Queue is paused for 30 seconds as the target server refused the last 5 players.", NamedTextColor.RED));
            }

            return;
        }

        // Gets the queue to send the next player from.
        SubQueue queue = getNextSubQueue(false);
        QueuedPlayer toSend = queue.removeFirst();
        toSend.queue(null);
        rememberPosition(toSend.uuid(), 0);
        Player player = toSend.player();

        // The player is null or the player's connection is no longer active, return
        if (player == null || !player.isActive())
            return;

        // Make sure the server the player is being sent to isn't the one they're currently on
        if (player.getCurrentServer().map(server -> server.getServerInfo().getName()).orElse("unknown").equalsIgnoreCase(this.name))
            return;

        player.sendMessage(Component.text("You are being sent to " + formattedName + "...", NamedTextColor.GREEN));
        QueuePlugin.debug("Sending " + player.getUsername() + " to " + formattedName + " via the " + queue.name()+ " queue.");

        player.createConnectionRequest(server).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                player.sendMessage(Component.text("You have been sent to " + formattedName + ".", NamedTextColor.GREEN));
                failedAttempts = 0;
                sendProgressMessages(queue);
                plugin.logger().info("{} has been sent to {} via queue.", player.getUsername(), formattedName);
            } else {
                player.sendMessage(Component.text("Unable to connect you to " + formattedName + ".", NamedTextColor.RED));

                Component reason = switch (result.getStatus()) {
                    case CONNECTION_IN_PROGRESS -> Component.text("You are already being connected to this server!", NamedTextColor.RED);
                    case SERVER_DISCONNECTED -> result.getReasonComponent().isPresent() ? result.getReasonComponent().get() : Component.text("The target server has refused your connection.", NamedTextColor.RED);
                    case ALREADY_CONNECTED -> Component.text("You are already connected to this server!", NamedTextColor.RED);
                    case CONNECTION_CANCELLED -> Component.text("Your connection has been cancelled unexpectedly.", NamedTextColor.RED);
                    default -> Component.text("", NamedTextColor.RED);
                };

                player.sendMessage(Component.text("Reason: ", reason.colorIfAbsent(NamedTextColor.RED).color()).append(reason));
            }
        }).exceptionally(e -> {
            plugin.logger().error("An exception occurred while trying to send {} to {}", player.getUsername(), formattedName, e);
            player.sendMessage(Component.text("Unable to connect you to " + formattedName + ".", NamedTextColor.RED));
            player.sendMessage(Component.text("Attempting to re-queue you...", NamedTextColor.RED));
            toSend.queue(this);
            queue.addToHead(toSend);
            failedAttempts++;
            return null;
        });

        lastSendTime = Instant.now();
    }

    public boolean canSend() {
        boolean paused = paused();
        if (paused && unpauseTime().isBefore(Instant.now())) {
            unpause();
            failedAttempts = 0;
            paused = false;
        }

        return !paused
                && lastSendTime.plus(TIME_BETWEEN_SENDS).isBefore(Instant.now())
                && server.getPlayersConnected().size() < maxPlayers
                && hasPlayers()
                && !getNextSubQueue(true).players().isEmpty();
    }

    public void sendProgressMessages(SubQueue queue) {
        if (queue.lastPositionMessageTime().plusSeconds(3).isAfter(Instant.now()))
            return;

        queue.lastPositionMessageTime(Instant.now());
        final boolean paused = this.paused();

        int index = 0;
        final Deque<QueuedPlayer> players = queue.players();
        for (QueuedPlayer player : players) {
            rememberPosition(player.uuid(), index);

            player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(index + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(players.size(), NamedTextColor.GREEN).append(Component.text(" for " + formattedName + ".", NamedTextColor.YELLOW))))));

            if (paused) {
                sendPausedQueueMessage(player);
            }

            index++;
        }
    }

    public void enqueue(QueuedPlayer player) {
        if (player.queue() != null) {
            if (player.queue().equals(this)) {
                player.sendMessage(Component.text("You are already queued for this server.", NamedTextColor.RED));
                return;
            } else {
                player.sendMessage(Component.text("You have been removed from the queue for " + player.queue().getServerFormatted() + ".", NamedTextColor.RED));
                plugin.logger().info("{} has been removed from the queue, because they joined the queue for another.", player.name());
                player.queue().remove(player);
            }
        }

        SubQueue subQueue = getSubQueue(player);
        player.queue(this);
        final int position = addToQueue(player, subQueue);

        player.sendMessage(Component.text("You have joined the queue for " + formattedName + ".", NamedTextColor.GREEN));
        player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(position + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(subQueue.players().size(), NamedTextColor.GREEN)).append(Component.text(".", NamedTextColor.YELLOW)))));

        if (!player.priority().message().equals(Component.empty()))
            player.sendMessage(player.priority().message());

        if (paused()) {
            sendPausedQueueMessage(player);
        }
    }

    /**
     * Adds a player to a sub queue they are not already part of.
     *
     * @param player The player to add
     * @param subQueue The sub queue to add the player to.
     * @return The player's position within the sub queue
     */
    private int addToQueue(QueuedPlayer player, SubQueue subQueue) {
        final int size = subQueue.players().size();
        if (size == 0) {
            subQueue.addToTail(player);
            return 1;
        }

        final OptionalInt rememberedPosition = getRememberedPosition(player.uuid());

        int weight = player.priority().weight;
        if (weight == 0 && rememberedPosition.isEmpty()) {
            // no remembered position and no weight, add to the end of the queue
            subQueue.addToTail(player);
            return size + 1;
        }

        if (rememberedPosition.isPresent() && rememberedPosition.getAsInt() <= 0) {
            subQueue.addToHead(player);
            return 0;
        }

        final Iterator<QueuedPlayer> playerIterator = subQueue.players().iterator();
        final int direction = 1;
        int position = 0;

        QueuedPlayer prev = null;
        while (playerIterator.hasNext()) {
            final QueuedPlayer next = playerIterator.next();

            if (weight > next.priority().weight || (rememberedPosition.isPresent() && position >= rememberedPosition.getAsInt())) {
                if (prev == null) {
                    subQueue.addToHead(player);
                    return 0;
                } else {
                    subQueue.addAfterPlayer(player, prev);
                    return position;
                }
            }

            prev = next;
            position += direction;
        }

        subQueue.addToTail(player);
        return size + 1;
    }

    public void remove(QueuedPlayer player) {
        player.queue(null);

        for (SubQueue subQueue : this.subQueues) {
            if (subQueue.hasPlayer(player)) {
                final int position = subQueue.playerPosition(player);
                if (position != -1) {
                    rememberPosition(player.uuid(), position);
                    subQueue.removePlayer(player);
                    break;
                }
            }
        }
    }

    public boolean hasPlayer(QueuedPlayer player) {
        for (SubQueue subQueue : this.subQueues)
            if (subQueue.hasPlayer(player))
                return true;

        return false;
    }

    public boolean hasPlayers() {
        for (SubQueue subQueue : this.subQueues)
            if (!subQueue.players().isEmpty())
                return true;

        return false;
    }

    /**
     * @param dry If dry is set to true, the sends won't be reset.
     * @return The queue to send the next player from.
     */
    public SubQueue getNextSubQueue(boolean dry) {
        return this.subQueueRatio.next(dry, NOT_EMPTY_PREDICATE, regularQueue);
    }

    public SubQueue getSubQueue(QueuedPlayer player) {
        for (SubQueue subQueue : this.subQueues)
            if (player.priority().weight >= subQueue.weight)
                return subQueue;

        // Fallback to the regular queue if none is found.
        return regularQueue;
    }

    public RegisteredServer getServer() {
        return server;
    }

    public String getServerFormatted() {
        return formattedName;
    }

    public void sendPausedQueueMessage(final QueuedPlayer player) {
        player.sendMessage(Component.text("The queue you are currently in is paused.", NamedTextColor.GRAY));

        final String reason = pauseReason();
        if (reason != null)
            player.sendMessage(Component.text("Reason: ", NamedTextColor.GRAY).append(Component.text(reason, Style.style(TextDecoration.ITALIC))));
    }

    public Vector<QueuedPlayer> allPlayers() {
        Vector<QueuedPlayer> allPlayers = new Vector<>();
        for (SubQueue subQueue : subQueues)
            allPlayers.addAll(subQueue.players());

        return allPlayers;
    }

    public SubQueue getRegularQueue() {
        return this.regularQueue;
    }

    public Ratio<SubQueue> getSubQueueRatio() {
        return subQueueRatio;
    }

    public int maxPlayers() {
        return this.maxPlayers;
    }

    public abstract boolean paused();

    public void pause(final Instant unpauseTime) {
        pause(unpauseTime, null);
    }

    public abstract void pause(final Instant unpauseTime, final @org.jspecify.annotations.Nullable String reason);

    public abstract void unpause();

    public abstract Instant unpauseTime();

    public abstract @Nullable String pauseReason();

    public abstract void rememberPosition(final UUID playerUUID, final int position);

    public abstract OptionalInt getRememberedPosition(final UUID playerUUID);

    public abstract void forgetPosition(final UUID playerUUID);
}

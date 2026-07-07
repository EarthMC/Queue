package net.earthmc.queue;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.queue.config.SubQueueTemplate;
import net.earthmc.queue.object.ConnectionResult;
import net.earthmc.queue.object.Ratio;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Represents a queue for a server.
 */
public abstract class Queue {
    private static final Duration TIME_BETWEEN_SENDS = Duration.ofMillis(500);
    private static final Predicate<SubQueue> NOT_EMPTY_PREDICATE = subQueue -> !subQueue.players().isEmpty();
    protected static final Duration REMEMBERED_POSITION_TIME = Duration.ofMinutes(15);

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

    private Instant lastNonEmptyTime = Instant.EPOCH;
    private boolean lastPaused = false;

    public Queue(RegisteredServer server, QueuePlugin plugin) {
        this.server = server;
        this.plugin = plugin;

        String name = server.getServerInfo().getName();
        this.name = name.toLowerCase(Locale.ROOT);

        this.formattedName = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);

        refreshMaxPlayers();
        this.subQueues = this.createFromTemplates(plugin.config().subQueueTemplates());
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
            final String reason = "Queue is paused for 30 seconds as the target server refused the last 5 players.";
            pause(Instant.now().plusSeconds(30), reason);
            for (QueuedPlayer player : allPlayers()) {
                player.sendMessage(Component.text(reason, NamedTextColor.RED));
            }

            plugin.logger().warn("Automatically pausing the queue for {} for 30s as the target server refused the last 5 players.", formattedName);
            failedAttempts = 0;
            return;
        }

        // Gets the queue to send the next player from.
        SubQueue queue = getNextSubQueue(false);
        QueuedPlayer toSend = queue.removeFirst();
        toSend.queue(null);
        rememberPosition(toSend.uuid(), 0);

        // the player's connection is no longer active
        if (!toSend.isConnected())
            return;

        toSend.sendMessage(Component.text("You are being sent to " + formattedName + "...", NamedTextColor.GREEN));
        QueuePlugin.debug("Sending " + toSend.name() + " to " + formattedName + " via the " + queue.name()+ " queue.");

        toSend.sendToServer(this.server).thenAccept(result -> {
            if (result == null) {
                // couldn't send the player and we shouldn't re-queue
                return;
            }

            if (result instanceof ConnectionResult.Resulted(boolean success) && success) {
                toSend.sendMessage(Component.text("You have been sent to " + formattedName + ".", NamedTextColor.GREEN));
                failedAttempts = 0;
                sendProgressMessages(queue);
                plugin.logger().info("{} has been sent to {} via queue.", toSend.name(), formattedName);
            } else {
                toSend.sendMessage(Component.text("Unable to connect you to " + formattedName + ".", NamedTextColor.RED));

                if (result instanceof ConnectionResult.FailedWithMessage(Component reason)) {
                    toSend.sendMessage(Component.text("Reason: ", reason.colorIfAbsent(NamedTextColor.RED).color()).append(reason));
                }

                toSend.sendMessage(Component.text("Attempting to re-queue you...", NamedTextColor.RED));
                toSend.queue(this);
                queue.addToHead(toSend);
                plugin.logger().warn("Failed to send {} to {}: {}", toSend.name(), formattedName, result instanceof ConnectionResult.FailedWithMessage(Component reason) ? PlainTextComponentSerializer.plainText().serialize(reason) : "No reason provided.");
                failedAttempts++;
            }
        });

        lastSendTime = Instant.now();
    }

    public boolean canSend() {
        // The server hasn't been turned on since the start of this proxy instance. (or, it really does just only allow 0 players)
        if (maxPlayers <= 0) {
            return false;
        }

        final Instant now = Instant.now();
        if (!lastPaused && lastNonEmptyTime.plusSeconds(5).isBefore(now)) {
            return false;
        }

        boolean paused = lastPaused = paused();
        if (paused && unpauseTime().isBefore(now)) {
            unpause();
            paused = false;
        }

        Boolean empty = null;
        final boolean ret = !paused
                && lastSendTime.plus(TIME_BETWEEN_SENDS).isBefore(now)
                && connectedPlayerCount() < maxPlayers
                && !(empty = getNextSubQueue(true).players().isEmpty());

        if (empty == Boolean.FALSE) {
            lastNonEmptyTime = now;
        }

        return ret;
    }

    public void sendProgressMessages(SubQueue queue) {
        if (queue.lastPositionMessageTime().plusSeconds(3).isAfter(Instant.now()))
            return;

        queue.lastPositionMessageTime(Instant.now());
        final boolean paused = this.paused();
        String pauseReason = null;

        if (paused) {
            pauseReason = this.pauseReason();
        }

        int index = 0;
        final Deque<QueuedPlayer> players = queue.players();
        final int playerCount = players.size();
        for (QueuedPlayer player : players) {
            rememberPosition(player.uuid(), index);

            player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(index + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(playerCount, NamedTextColor.GREEN).append(Component.text(" for " + formattedName + ".", NamedTextColor.YELLOW))))));

            if (paused) {
                sendPausedQueueMessage(player, pauseReason);
            }

            index++;
        }
    }

    public void enqueue(QueuedPlayer player) {
        final Queue currQueue = player.queue();
        if (currQueue != null) {
            if (currQueue.equals(this)) {
                player.sendMessage(Component.text("You are already queued for this server.", NamedTextColor.RED));
                return;
            } else {
                player.sendMessage(Component.text("You have been removed from the queue for " + currQueue.getServerFormatted() + ".", NamedTextColor.RED));
                plugin.logger().info("{} has been removed from the queue, because they joined the queue for another.", player.name());
                currQueue.remove(player);
            }
        }

        SubQueue subQueue = getSubQueue(player);
        player.queue(this);
        final int position = addToQueue(player, subQueue);

        player.sendMessage(Component.text("You have joined the queue for " + formattedName + ".", NamedTextColor.GREEN));
        player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(position + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(subQueue.players().size(), NamedTextColor.GREEN)).append(Component.text(".", NamedTextColor.YELLOW)))));

        final Priority priority = player.priority();
        if (!priority.message().equals(Component.empty())) {
            player.sendMessage(priority.message());
        }

        if (paused()) {
            sendPausedQueueMessage(player, pauseReason());
        }
        this.wakeup();
    }

    /**
     * Adds a player to a sub queue they are not already part of.
     *
     * @param player The player to add
     * @param subQueue The sub queue to add the player to.
     * @return The player's position within the sub queue, 0-indexed
     */
    protected int addToQueue(QueuedPlayer player, SubQueue subQueue) {
        final int size = subQueue.players().size();
        if (size == 0) {
            subQueue.addToTail(player);
            return 0;
        }

        final OptionalInt rememberedPosition = getRememberedPosition(player.uuid());

        int weight = player.priority().weight;
        if (weight <= subQueue.weight() && (rememberedPosition.isEmpty() || rememberedPosition.getAsInt() >= size - 1)) {
            // no remembered position and no extra weight, add to the end of the queue
            subQueue.addToTail(player);
            return size;
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
        final Priority priority = player.priority();
        for (SubQueue subQueue : this.subQueues)
            if (priority.weight >= subQueue.weight)
                return subQueue;

        // Fallback to the regular queue if none is found.
        return regularQueue;
    }

    public List<SubQueue> getSubQueues() {
        return subQueues;
    }

    public RegisteredServer getServer() {
        return server;
    }

    public String getServerFormatted() {
        return formattedName;
    }

    public void sendPausedQueueMessage(final QueuedPlayer player, final @Nullable String reason) {
        player.sendMessage(Component.text("The queue you are currently in is paused.", NamedTextColor.GRAY));

        if (reason != null)
            player.sendMessage(Component.text("Reason: ", NamedTextColor.GRAY).append(Component.text(reason, Style.style(TextDecoration.ITALIC))));
    }

    public @Unmodifiable Collection<QueuedPlayer> allPlayers() {
        ImmutableSet.Builder<QueuedPlayer> allPlayers = ImmutableSet.builder();
        for (SubQueue subQueue : subQueues) {
            allPlayers.addAll(subQueue.players());
        }

        return allPlayers.build();
    }

    public int playerCount() {
        int count = 0;
        for (final SubQueue subQueue : this.subQueues) {
            count += subQueue.players().size();
        }

        return count;
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

    public String getName() {
        return name;
    }

    public void wakeup() {
        this.lastNonEmptyTime = Instant.now();
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

    public abstract int connectedPlayerCount();

    @ApiStatus.OverrideOnly
    public abstract List<SubQueue> createFromTemplates(final List<SubQueueTemplate> templates);
}

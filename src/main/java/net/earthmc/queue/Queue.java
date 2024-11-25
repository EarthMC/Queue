package net.earthmc.queue;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.queue.object.Ratio;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Represents a queue for a server.
 */
public class Queue {
    private static final Duration TIME_BETWEEN_SENDS = Duration.ofMillis(1000);
    private static final Predicate<SubQueue> NOT_EMPTY_PREDICATE = subQueue -> !subQueue.players().isEmpty();

    private final QueuePlugin plugin;
    private final List<SubQueue> subQueues;
    private final SubQueue regularQueue;
    private final Ratio<SubQueue> subQueueRatio;
    private final Cache<UUID, Integer> rememberedPlayers = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).build();
    private final RegisteredServer server;
    private final String formattedName;
    private final String name;

    private int maxPlayers;
    private boolean paused;
    private String pauseReason;
    private Instant unpauseTime = Instant.MAX;
    private Instant lastSendTime = Instant.EPOCH;
    private int failedAttempts;

    public Queue(RegisteredServer server, QueuePlugin plugin) {
        this.server = server;
        this.plugin = plugin;

        String name = server.getServerInfo().getName();
        this.name = name.toLowerCase(Locale.ROOT);

        this.formattedName = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);

        refreshMaxPlayers();
        this.subQueues = plugin.config().newSubQueues();
        this.subQueueRatio = new Ratio<>(this.subQueues);
        this.regularQueue = getLastElement(subQueues);
    }

    /**
     * Only used for tests
     */
    @VisibleForTesting
    public Queue(List<SubQueue> subQueues) {
        this.subQueues = subQueues;
        this.formattedName = "TestQueue";
        this.name = "testqueue";
        this.server = null;
        this.plugin = null;

        this.subQueueRatio = new Ratio<>(this.subQueues);
        this.regularQueue = getLastElement(subQueues);
    }

    public void refreshMaxPlayers() {
        server.ping().thenAccept(ping -> {
            if (ping.getPlayers().isPresent())
                this.maxPlayers = ping.getPlayers().get().getMax();
        });
    }

    public void sendNext() {
        if (!canSend())
            return;

        if (failedAttempts >= 5) {
            pause(true, Instant.now().plusSeconds(30));
            for (QueuedPlayer player : allPlayers())
                player.sendMessage(Component.text("Queue is paused for 30 seconds as the target server refused the last 5 players.", NamedTextColor.RED));

            return;
        }

        // Gets the queue to send the next player from.
        SubQueue queue = getNextSubQueue(false);
        QueuedPlayer toSend = queue.removePlayer(0);
        toSend.queue(null);
        rememberPosition(toSend, 0);
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
            queue.addPlayer(toSend, 0);
            failedAttempts++;
            return null;
        });

        lastSendTime = Instant.now();
    }

    public boolean canSend() {
        if (paused && unpauseTime.isBefore(Instant.now())) {
            paused = false;
            unpauseTime = Instant.MAX;
            failedAttempts = 0;
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

        for (QueuedPlayer player : queue.players()) {
            rememberPosition(player);

            player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(player.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(queue.players().size(), NamedTextColor.GREEN).append(Component.text(" for " + formattedName + ".", NamedTextColor.YELLOW))))));

            if (paused)
                sendPausedQueueMessage(player);
        }
    }

    public void rememberPosition(QueuedPlayer player) {
        rememberPosition(player, player.position());
    }

    public void rememberPosition(QueuedPlayer player, int index) {
        rememberedPlayers.put(player.uuid(), index);
    }

    public void enqueue(QueuedPlayer player) {
        enqueue(player, true);
    }

    public void enqueue(QueuedPlayer player, boolean confirmation) {
        if (player.queue() != null) {
            if (player.queue().equals(this)) {
                player.sendMessage(Component.text("You are already queued for this server.", NamedTextColor.RED));
                return;
            } else {
                if (!confirmation) {
                    player.sendMessage(Component.text("You already already queued for another server, use /joinqueue " + this.name + " confirm to confirm.", NamedTextColor.RED));
                    return;
                } else {
                    player.sendMessage(Component.text("You have been removed from the queue for " + player.queue().getServerFormatted() + ".", NamedTextColor.RED));
                    plugin.logger().info("{} has been removed from the queue, because they joined the queue for another.", player.name());
                    player.queue().remove(player);
                }
            }
        }

        SubQueue subQueue = getSubQueue(player);
        player.queue(this);

        int index = insertionIndex(player, subQueue);
        if (index < 0 || index >= subQueue.players().size())
            subQueue.addPlayer(player);
        else
            subQueue.addPlayer(player, index);

        player.sendMessage(Component.text("You have joined the queue for " + formattedName + ".", NamedTextColor.GREEN));
        player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(player.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(subQueue.players().size(), NamedTextColor.GREEN)).append(Component.text(".", NamedTextColor.YELLOW)))));

        if (!player.priority().message().equals(Component.empty()))
            player.sendMessage(player.priority().message());

        if (paused) {
            sendPausedQueueMessage(player);
        }
    }

    public int insertionIndex(QueuedPlayer player, SubQueue subQueue) {
        if (subQueue.players().isEmpty())
            return 0;

        int rememberedPosition = Optional.ofNullable(rememberedPlayers.getIfPresent(player.uuid())).orElse(subQueue.players().size());

        int weight = player.priority().weight;
        if (weight == 0)
            return rememberedPosition;

        int slot = 0;
        for (int i = 0; i < subQueue.players().size(); i++) {
            if (weight <= subQueue.getPlayer(i).priority().weight)
                slot = i + 1;
        }

        int priorityIndex = Math.min(slot, subQueue.players().size());

        return Math.min(rememberedPosition, priorityIndex);
    }

    public void remove(QueuedPlayer player) {
        rememberPosition(player);
        player.queue(null);

        for (SubQueue subQueue : this.subQueues)
            subQueue.removePlayer(player);
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

    public boolean paused() {
        return paused;
    }

    public void pause(boolean paused) {
        pause(paused, Instant.MAX, null);
    }

    public void pause(boolean paused, Instant unpauseTime) {
        pause(paused, unpauseTime, null);
    }

    public void pause(boolean paused, Instant unpauseTime, @Nullable String reason) {
        this.paused = paused;
        this.unpauseTime = unpauseTime;
        this.pauseReason = reason;
        this.failedAttempts = 0;
    }

    public void sendPausedQueueMessage(final QueuedPlayer player) {
        if (!paused)
            return;

        player.sendMessage(Component.text("The queue you are currently in is paused.", NamedTextColor.GRAY));

        if (pauseReason != null)
            player.sendMessage(Component.text("Reason: ", NamedTextColor.GRAY).append(Component.text(pauseReason, Style.style(TextDecoration.ITALIC))));
    }

    @NotNull
    public Instant unpauseTime() {
        return this.unpauseTime;
    }

    @Nullable
    public String pauseReason() {
        return this.pauseReason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;

        if (!(other instanceof Queue queue))
            return false;

        return this.server.getServerInfo().getName().equalsIgnoreCase(queue.getServer().getServerInfo().getName());
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

    private SubQueue getLastElement(Collection<SubQueue> collection) {
        SubQueue current = null;

        for (SubQueue subQueue : collection)
            current = subQueue;

        return current;
    }

    public void forget(UUID uuid) {
        this.rememberedPlayers.invalidate(uuid);
    }

    public Ratio<SubQueue> getSubQueueRatio() {
        return subQueueRatio;
    }

    public int maxPlayers() {
        return this.maxPlayers;
    }
}

package net.earthmc.queue;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

/**
 * Represents a queue for a server.
 */
public class Queue {
    private final Duration TIME_BETWEEN_SENDS = Duration.ofMillis(1000);
    private final SubQueue regularQueue = new SubQueue(SubQueueType.REGULAR);
    private final SubQueue priorityQueue = new SubQueue(SubQueueType.PRIORITY);
    private final SubQueue premiumQueue = new SubQueue(SubQueueType.PREMIUM);
    private final Cache<UUID, Integer> rememberedPlayers = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).build();
    private final RegisteredServer server;
    private final String formattedName;

    private int maxPlayers;
    private boolean paused;
    private Instant unpauseTime = Instant.MAX;
    private Instant lastSendTime = Instant.EPOCH;
    private int failedAttempts;

    public Queue(RegisteredServer server) {
        this.server = server;

        String name = server.getServerInfo().getName();
        if (name.equalsIgnoreCase("towny"))
            name = "EarthMC";

        this.formattedName = name.substring(0, 1).toUpperCase() + name.substring(1);

        refreshMaxPlayers();
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
                player.player().sendMessage(Component.text("Queue is paused for 30 seconds as the target server refused the last 5 players.", NamedTextColor.RED));

            return;
        }

        // Gets the queue to send the next player from.
        SubQueue queue = getQueue(false);
        QueuedPlayer toSend = queue.players.get(0);
        rememberPosition(toSend);
        Player player = toSend.player();

        player.sendMessage(Component.text("You are being sent to " + formattedName + "...", NamedTextColor.GREEN));

        player.createConnectionRequest(server).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                if (queue.type != SubQueueType.REGULAR)
                    queue.sends++;

                player.sendMessage(Component.text("You have been sent to " + formattedName + ".", NamedTextColor.GREEN));
                queue.players.remove(0);
                toSend.queue(null);
                failedAttempts = 0;
                sendProgressMessages(queue);
            } else {
                player.sendMessage(Component.text("Unable to connect you to " + formattedName + ".", NamedTextColor.RED));
                failedAttempts++;
            }
        }).exceptionally(e -> {
            player.sendMessage(Component.text("Unable to connect you to " + formattedName + ".", NamedTextColor.RED));
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

        return !paused && !getQueue(true).players.isEmpty()
                && server.getPlayersConnected().size() < maxPlayers
                && lastSendTime.plus(TIME_BETWEEN_SENDS).isBefore(Instant.now());
    }

    public void sendProgressMessages(SubQueue queue) {
        if (queue.lastPositionMessageTime.plusSeconds(3).isBefore(Instant.now()))
            return;

        queue.lastPositionMessageTime = Instant.now();

        for (QueuedPlayer player : queue.players) {
            rememberPosition(player);

            player.player().sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(player.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(queue.players.size(), NamedTextColor.GREEN).append(Component.text(" for " + formattedName, NamedTextColor.YELLOW))))));
            if (paused)
                player.player().sendMessage(Component.text("The queue you are currently in is paused.", NamedTextColor.GRAY));
        }
    }

    public void rememberPosition(QueuedPlayer player) {
        rememberedPlayers.put(player.player().getUniqueId(), player.position());
    }

    public void enqueue(QueuedPlayer player) {
        if (player.queue() != null) {
            if (player.queue().equals(this)) {
                player.player().sendMessage(Component.text("You are already queued for this server.", NamedTextColor.RED));
                return;
            } else {
                player.player().sendMessage(Component.text("You have been removed from the queue for " + player.queue().getServerFormatted() + ".", NamedTextColor.RED));
                player.queue().remove(player);
            }
        }

        SubQueue queue = getQueue(player);
        player.queue(this);

        int index = insertionIndex(player, queue);
        if (index < 0 || index >= queue.players.size())
            queue.players.add(player);
        else
            queue.players.add(index, player);

        player.player().sendMessage(Component.text("You have joined the queue for " + formattedName + ".", NamedTextColor.GREEN));
        player.player().sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(player.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(queue.players.size(), NamedTextColor.GREEN)))));

        if (player.priorityQueue())
            player.player().sendMessage(player.priority().message());

        if (paused)
            player.player().sendMessage(Component.text("The queue you are currently in is paused.", NamedTextColor.GRAY));
    }

    public int insertionIndex(QueuedPlayer player, SubQueue queue) {
        if (queue.players.isEmpty())
            return 0;

        int rememberedPosition = queue.players.size();
        if (rememberedPlayers.getIfPresent(player.player().getUniqueId()) != null)
            rememberedPosition = Math.min(rememberedPlayers.getIfPresent(player.player().getUniqueId()), queue.players.size());

        int weight = player.priority().weight();
        if (weight == 0)
            return rememberedPosition;

        int slot = 0;
        for (int i = 0; i < queue.players.size(); i++) {
            if (weight <= queue.players.get(i).priority().weight())
                slot = i+1;
        }

        int priorityIndex = Math.min(slot, queue.players.size());

        return Math.min(rememberedPosition, priorityIndex);
    }

    public void remove(QueuedPlayer player) {
        rememberPosition(player);

        regularQueue.players.remove(player);
        priorityQueue.players.remove(player);
        premiumQueue.players.remove(player);
    }

    public boolean hasPlayer(QueuedPlayer player) {
        return regularQueue.players.contains(player) || priorityQueue.players.contains(player) || premiumQueue.players.contains(player);
    }

    /**
     * @param dry Sends won't be decremented for dry checks.
     * @return The queue to send the next player from.
     */
    private SubQueue getQueue(boolean dry) {
        if (premiumQueue.sends > 3 || premiumQueue.players.isEmpty()) {
            if (premiumQueue.sends > 3 && !dry)
                premiumQueue.sends = 0;

            if (priorityQueue.sends > 0 || priorityQueue.players.isEmpty()) {
                if (priorityQueue.sends > 0 && !dry)
                    priorityQueue.sends = 0;

                return regularQueue;
            }
            return priorityQueue;
        }
        return premiumQueue;
    }

    public SubQueue getQueue(QueuedPlayer player) {
        return getQueueByType(player.priority().queueType());
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
        pause(paused, Instant.MAX);
    }

    public void pause(boolean paused, Instant unpauseTime) {
        this.paused = paused;
        this.unpauseTime = unpauseTime;
        this.failedAttempts = 0;
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
        allPlayers.addAll(regularQueue.players);
        allPlayers.addAll(priorityQueue.players);
        allPlayers.addAll(premiumQueue.players);

        return allPlayers;
    }

    public enum SubQueueType {
        REGULAR, PRIORITY, PREMIUM;
    }

    public class SubQueue {
        public final SubQueueType type;
        public Vector<QueuedPlayer> players = new Vector<>();
        public int sends = 0;
        public Instant lastPositionMessageTime = Instant.EPOCH;

        public SubQueue(SubQueueType type) {
            this.type = type;
        }

        public Vector<QueuedPlayer> players() {
            return players;
        }

        public SubQueueType type() {
            return type;
        }
    }

    public SubQueue getQueueByType(SubQueueType type) {
        return switch (type) {
            case REGULAR -> regularQueue;
            case PRIORITY -> priorityQueue;
            case PREMIUM -> premiumQueue;
        };
    }
}

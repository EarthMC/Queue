package net.earthmc.queue;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.Duration;
import java.time.Instant;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

/**
 * Represents a queue for a server.
 */
public class Queue {
    private final Duration TIME_BETWEEN_SENDS = Duration.ofMillis(1000);
    private final Vector<QueuedPlayer> regularQueue = new Vector<>();
    private final Vector<QueuedPlayer> priorityQueue = new Vector<>();
    private final Cache<String, Integer> rememberedPlayers = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).build();
    private final RegisteredServer server;
    private final String formattedName;

    private int maxPlayers;
    private boolean paused;
    private Instant unpauseTime = Instant.MAX;
    private Instant lastSendTime = Instant.EPOCH;
    private Instant lastPositionMessageTime = Instant.EPOCH;
    private int failedAttempts;
    private int prioritySends;

    public Queue(RegisteredServer server) {
        this.server = server;
        String name = server.getServerInfo().getName();
        this.formattedName = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();

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
        Vector<QueuedPlayer> queue = getQueue();
        QueuedPlayer toSend = queue.get(0);
        rememberPosition(toSend);
        Player player = toSend.player();

        player.sendMessage(Component.text("You are being sent to " + formattedName + "...", NamedTextColor.GREEN));

        player.createConnectionRequest(server).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                if (toSend.priorityQueue())
                    prioritySends++;

                player.sendMessage(Component.text("You have been sent to " + formattedName + ".", NamedTextColor.GREEN));
                queue.remove(0);
                toSend.queue(null);
                failedAttempts = 0;
                sendProgressMessages();
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

        return !paused && !getQueue().isEmpty()
                && server.getPlayersConnected().size() < maxPlayers
                && lastSendTime.plus(TIME_BETWEEN_SENDS).isBefore(Instant.now());
    }

    public void sendProgressMessages() {
        if (lastPositionMessageTime.plusSeconds(3).isBefore(Instant.now()))
            return;

        lastPositionMessageTime = Instant.now();

        for (QueuedPlayer player : allPlayers()) {
            rememberPosition(player);
            Vector<QueuedPlayer> queue = getQueue(player);

            player.player().sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(player.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(queue.size(), NamedTextColor.GREEN).append(Component.text(" for " + formattedName, NamedTextColor.YELLOW))))));
            if (paused)
                player.player().sendMessage(Component.text("The queue you are currently in is paused.", NamedTextColor.GRAY));
        }
    }

    public void rememberPosition(QueuedPlayer player) {
        rememberedPlayers.put(player.player().getUsername(), player.position());
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

        Vector<QueuedPlayer> queue = getQueue(player);
        player.queue(this);

        int index = insertionIndex(player, queue);
        if (index < 0 || index >= queue.size())
            queue.add(player);
        else
            queue.add(index, player);

        player.player().sendMessage(Component.text("You have joined the queue for " + formattedName + ".", NamedTextColor.GREEN));
        player.player().sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(player.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(queue.size(), NamedTextColor.GREEN)))));

        if (player.priorityQueue())
            player.player().sendMessage(player.priority().message());

        if (paused)
            player.player().sendMessage(Component.text("The queue you are currently in is paused.", NamedTextColor.GRAY));
    }

    public int insertionIndex(QueuedPlayer player, Vector<QueuedPlayer> queue) {
        if (queue.isEmpty())
            return 0;

        int rememberedPosition = queue.size();
        if (rememberedPlayers.getIfPresent(player.player().getUsername()) != null)
            rememberedPosition = Math.min(rememberedPlayers.getIfPresent(player.player().getUsername()), queue.size());

        int weight = player.priority().weight();
        if (weight == 0)
            return rememberedPosition;

        int slot = 0;
        for (int i = 0; i < queue.size(); i++) {
            if (weight <= queue.get(i).priority().weight())
                slot = i+1;
        }

        int priorityIndex = Math.min(slot, queue.size());

        return Math.min(rememberedPosition, priorityIndex);
    }

    public void remove(QueuedPlayer player) {
        rememberPosition(player);

        regularQueue.remove(player);
        priorityQueue.remove(player);
    }

    public boolean hasPlayer(QueuedPlayer player) {
        return regularQueue.contains(player) || priorityQueue.contains(player);
    }

    private Vector<QueuedPlayer> getQueue() {
        if (prioritySends > 3 || priorityQueue.isEmpty())
            return regularQueue;
        else
            return priorityQueue;
    }

    public Vector<QueuedPlayer> getQueue(QueuedPlayer player) {
        return player.priorityQueue() ? priorityQueue : regularQueue;
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
        allPlayers.addAll(regularQueue);
        allPlayers.addAll(priorityQueue);

        return allPlayers;
    }
}

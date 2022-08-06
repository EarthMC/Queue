package net.earthmc.queue;

import net.earthmc.queue.object.Weighted;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Vector;

public class SubQueue extends Weighted {
    private final String name;
    private final Vector<QueuedPlayer> players = new Vector<>(0);
    private Instant lastPositionMessageTime = Instant.EPOCH;
    public final int maxSends;

    public SubQueue(String name, int weight, int maxSends) {
        super(weight);
        this.name = name;
        this.maxSends = maxSends;
    }

    public Vector<QueuedPlayer> players() {
        return this.players;
    }

    public boolean hasPlayer(@NotNull QueuedPlayer player) {
        return players.contains(player);
    }

    public void addPlayer(@NotNull QueuedPlayer player) {
        QueuePlugin.debug("Added player " + player.name() + " to subqueue " + this.name);
        players.add(player);
    }

    public void addPlayer(@NotNull QueuedPlayer player, int index) {
        QueuePlugin.debug("Added player " + player.name() + " to subqueue " + this.name + " at position " + index);
        players.add(index, player);
    }

    public void removePlayer(@NotNull QueuedPlayer player) {
        QueuePlugin.debug("Removed player " + player.name() + " from subqueue " + this.name);
        players.remove(player);
    }

    public QueuedPlayer removePlayer(int index) {
        QueuePlugin.debug("Removed player at position " + index + " from subqueue " + this.name);
        return players.remove(index);
    }

    @NotNull
    public QueuedPlayer getPlayer(int index) throws IndexOutOfBoundsException {
        return players.get(index);
    }

    public String name() {
        return name;
    }

    public int maxSends() {
        return maxSends;
    }

    public void lastPositionMessageTime(@NotNull Instant instant) {
        this.lastPositionMessageTime = instant;
    }

    public Instant lastPositionMessageTime() {
        return this.lastPositionMessageTime;
    }

    @Override
    public String toString() {
        return "SubQueue{" +
                "name='" + name + '\'' +
                ", players=" + players +
                ", lastPositionMessageTime=" + lastPositionMessageTime +
                ", maxSends=" + maxSends +
                ", weight=" + weight +
                '}';
    }

    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if (!(other instanceof SubQueue subQueue))
            return false;

        return subQueue.name().equals(this.name);
    }
}
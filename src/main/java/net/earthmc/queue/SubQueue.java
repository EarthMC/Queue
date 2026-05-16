package net.earthmc.queue;

import net.earthmc.queue.object.Weighted;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.Set;

@NullMarked
public abstract class SubQueue extends Weighted {
    private final String name;
    private Instant lastPositionMessageTime = Instant.EPOCH;
    public final int maxSends;

    public SubQueue(String name, int weight, int maxSends) {
        super(weight);
        this.name = name;
        this.maxSends = maxSends;
    }

    public abstract Deque<QueuedPlayer> players();

    public abstract Set<QueuedPlayer> playerSet();

    public void addPlayer(QueuedPlayer player) {
        QueuePlugin.debug("Added player " + player.name() + " to subqueue " + this.name);
        players().addLast(player);
    }

    public abstract void addAfterPlayer(QueuedPlayer player, QueuedPlayer anchor);

    public abstract int playerPosition(QueuedPlayer player);

    public boolean removePlayer(QueuedPlayer player) {
        if (playerSet().remove(player)) {
            QueuePlugin.debug("Removed player " + player.name() + " from subqueue " + this.name);
            players().remove(player);
            return true;
        } else {
            return false;
        }
    }

    public void addToTail(QueuedPlayer player) {
        QueuePlugin.debug("Added player " + player.name() + " to the end of subqueue " + this.name);
        players().addLast(player);
        playerSet().add(player);
    }

    public void addToHead(QueuedPlayer player) {
        QueuePlugin.debug("Added player " + player.name() + " to the head of subqueue " + this.name);
        players().addFirst(player);
        playerSet().add(player);
    }

    public QueuedPlayer removeFirst() throws NoSuchElementException {
        final QueuedPlayer player = players().removeFirst();
        QueuePlugin.debug("Removed player " + player.name() + " as the first player of subqueue " + this.name);
        playerSet().remove(player);

        return player;
    }

    public boolean hasPlayer(final QueuedPlayer player) {
        return playerSet().contains(player);
    }

    public String name() {
        return name;
    }

    public int maxSends() {
        return maxSends;
    }

    public void lastPositionMessageTime(Instant instant) {
        this.lastPositionMessageTime = instant;
    }

    public Instant lastPositionMessageTime() {
        return this.lastPositionMessageTime;
    }
}

package net.earthmc.queue.impl.local;

import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.earthmc.queue.SubQueue;
import org.jspecify.annotations.NullMarked;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public class LocalSubQueue extends SubQueue {
    private final LinkedList<QueuedPlayer> players = new LinkedList<>();
    private final Set<QueuedPlayer> playerSet = ConcurrentHashMap.newKeySet();

    public LocalSubQueue(String name, int weight, int maxSends) {
        super(name, weight, maxSends);
    }

    @Override
    public Deque<QueuedPlayer> players() {
        return players;
    }

    @Override
    public Set<QueuedPlayer> playerSet() {
        return playerSet;
    }

    @Override
    public void addAfterPlayer(QueuedPlayer player, QueuedPlayer anchor) {
        final int index = players.indexOf(anchor);
        if (index == -1) {
            throw new IllegalArgumentException("Provided anchor '" + anchor + "' is not part of subqueue " + this.name());
        }

        players.add(index + 1, player);
        playerSet.add(player);
        QueuePlugin.debug("Added player " + player.name() + " to subqueue " + this.name());
    }

    @Override
    public int playerPosition(QueuedPlayer player) {
        return players.indexOf(player);
    }
}

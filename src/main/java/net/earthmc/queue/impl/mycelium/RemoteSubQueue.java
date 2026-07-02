package net.earthmc.queue.impl.mycelium;

import net.earthmc.mycelium.api.Mycelium;
import net.earthmc.mycelium.api.store.collection.RelativeDeque;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.earthmc.queue.SubQueue;
import net.earthmc.queue.config.SubQueueTemplate;

import java.util.Deque;
import java.util.Set;

public class RemoteSubQueue extends SubQueue {
    private final RelativeDeque<QueuedPlayer> players;
    private final Set<QueuedPlayer> playerSet;

    public RemoteSubQueue(Queue queue, SubQueueTemplate template) {
        super(queue, template.name(), template.weight(), template.maxSends());

        final Mycelium api = Mycelium.api();
        players = api.dataStore().collections().blockingDeque("queue:" + queue.getName() + ":subqueue:" + name + ":queue", QueuedPlayerCodec.INSTANCE);
        playerSet = api.dataStore().collections().set("queue:" + queue.getName() + ":subqueue:" + name + ":set", QueuedPlayerCodec.INSTANCE);
    }

    @Override
    public Deque<QueuedPlayer> players() {
        return this.players;
    }

    @Override
    public Set<QueuedPlayer> playerSet() {
        return this.playerSet;
    }

    @Override
    public void addAfterPlayer(QueuedPlayer player, QueuedPlayer anchor) {
        players.addAfter(player, anchor);
        playerSet.add(player);
        QueuePlugin.debug("Added player " + player.name() + " to subqueue " + this.name());
    }

    @Override
    public int playerPosition(QueuedPlayer player) {
        return Math.toIntExact(players.indexOf(player));
    }
}

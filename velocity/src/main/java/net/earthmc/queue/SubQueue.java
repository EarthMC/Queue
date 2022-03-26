package net.earthmc.queue;

import net.earthmc.queue.object.Weighted;

import java.time.Instant;
import java.util.Vector;

public class SubQueue extends Weighted {
    public final String name;
    public final Vector<QueuedPlayer> players = new Vector<>();
    public int sends = 0;
    public Instant lastPositionMessageTime = Instant.EPOCH;
    public final int maxSends;

    public SubQueue(String name, int weight, int maxSends) {
        super(weight);
        this.name = name;
        this.maxSends = maxSends;
    }

    public Vector<QueuedPlayer> players() {
        return players;
    }

    public String name() {
        return name;
    }

    public int minWeight() {
        return weight;
    }

    public int maxSends() {
        return maxSends;
    }

    @Override
    public String toString() {
        return "SubQueue{" +
                "name='" + name + '\'' +
                ", players=" + players +
                ", sends=" + sends +
                ", lastPositionMessageTime=" + lastPositionMessageTime +
                ", maxSends=" + maxSends +
                ", weight=" + weight +
                '}';
    }
}
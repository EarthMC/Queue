package net.earthmc.queue.object;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;

public class Weighted implements Comparable<Weighted> {
    private static final Comparator<Weighted> comparator = Collections.reverseOrder(Comparator.comparingInt(w -> w.weight));
    public final int weight;

    public Weighted(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return this.weight;
    }

    @Override
    public int compareTo(@NotNull Weighted other) {
        return comparator.compare(this, other);
    }
}

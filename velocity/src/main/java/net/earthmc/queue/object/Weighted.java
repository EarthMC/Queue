package net.earthmc.queue.object;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class Weighted implements Comparable<Weighted> {
    private static final Comparator<Weighted> comparator = Comparator.comparing(Weighted::weight, Comparator.reverseOrder());
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

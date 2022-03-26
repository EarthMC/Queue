package net.earthmc.queue;

import net.earthmc.queue.object.Weighted;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class WeightedTests {
    @Test
    void testWeightedOrdering() {
        Weighted weight5 = new Weighted(5);
        Weighted weight3 = new Weighted(3);
        Weighted weight1 = new Weighted(1);
        Weighted negativeWeighted = new Weighted(-1);

        Set<Weighted> set = new ConcurrentSkipListSet<>();
        set.add(weight1);
        set.add(weight5);
        set.add(negativeWeighted);
        set.add(weight3);

        Iterator<Weighted> iterator = set.iterator();
        Assertions.assertEquals(5, iterator.next().weight);
        Assertions.assertEquals(3, iterator.next().weight);
        Assertions.assertEquals(1, iterator.next().weight);
        Assertions.assertEquals(-1, iterator.next().weight);
    }
}

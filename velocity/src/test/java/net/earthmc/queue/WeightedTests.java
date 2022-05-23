package net.earthmc.queue;

import net.earthmc.queue.object.Weighted;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WeightedTests {
    @Test
    void testWeightedOrdering() {
        Weighted weight5 = new Weighted(5);
        Weighted weight3 = new Weighted(3);
        Weighted weight1 = new Weighted(1);
        Weighted negativeWeighted = new Weighted(-1);

        List<Weighted> weights = new ArrayList<>();
        weights.add(weight1);
        weights.add(weight5);
        weights.add(negativeWeighted);
        weights.add(weight3);

        Collections.sort(weights);

        Iterator<Weighted> iterator = weights.iterator();
        assertEquals(5, iterator.next().weight);
        assertEquals(3, iterator.next().weight);
        assertEquals(1, iterator.next().weight);
        assertEquals(-1, iterator.next().weight);
    }
}

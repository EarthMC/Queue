package net.earthmc.queue;

import net.earthmc.queue.object.Ratio;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RatioTests {
    @Test
    void singleOptionRatioTest() {
        Ratio<Integer> ratio = new Ratio<>(Map.of(1, 1));

        assertEquals(1, ratio.next());
        assertEquals(1, ratio.next());
    }

    @Test
    void multiOptionRatioTest() {
        Map<Integer, Integer> ints = new LinkedHashMap<>();
        ints.put(1, 1);
        ints.put(2, 2);
        ints.put(3, 3);

        Ratio<Integer> ratio = new Ratio<>(ints);

        assertEquals(1, ratio.next());
        assertEquals(2, ratio.next());
        assertEquals(2, ratio.next());
        assertEquals(3, ratio.next());
        assertEquals(3, ratio.next());
        assertEquals(3, ratio.next());

        // Rolls over back to 1
        assertEquals(1, ratio.next());
    }

    @Test
    void dryRatioTest() {
        Map<Integer, Integer> ints = new LinkedHashMap<>();
        ints.put(1, 1);
        ints.put(2, 2);
        ints.put(3, 3);

        Ratio<Integer> ratio = new Ratio<>(ints);

        assertEquals(1, ratio.next(true));
        assertEquals(1, ratio.next(true));
        assertEquals(1, ratio.next(true));
    }

    @Test
    void testRatioWithDry() {
        Map<Integer, Integer> ints = new LinkedHashMap<>();
        ints.put(1, 1);
        ints.put(2, 2);
        ints.put(3, 3);

        Ratio<Integer> ratio = new Ratio<>(ints);

        assertEquals(1, ratio.next(true));
        assertEquals(1, ratio.next(false));
        assertEquals(2, ratio.next(false));
        assertEquals(2, ratio.next(true));
        assertEquals(2, ratio.next(true));
        assertEquals(2, ratio.next(true));
        assertEquals(2, ratio.next(false));
        assertEquals(3, ratio.next(false));
        assertEquals(3, ratio.next(false));
        assertEquals(3, ratio.next(false));
        assertEquals(1, ratio.next(false));
    }

    @Test
    void testRatioWithMatchingPredicate() {
        Map<Integer, Integer> ints = new LinkedHashMap<>();
        ints.put(1, 1);
        ints.put(2, 2);
        ints.put(3, 3);

        Ratio<Integer> ratio = new Ratio<>(ints);

        assertEquals(1, ratio.next(false));
        // 1 is now at it's max use of 1, if we supply a predicate of i == 1 we want it to return 1
        assertEquals(1, ratio.next(false, i -> i == 1, 2));
    }
}

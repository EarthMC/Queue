package net.earthmc.queue;

import net.earthmc.queue.object.Ratio;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class QueueTests {
    @Test
    void testSubQueueOrdering() {
        SubQueue regular = new SubQueue("regular", 0, 1);
        SubQueue priority = new SubQueue("priority", 1, 1);
        SubQueue premium = new SubQueue("premium", 5, 3);

        QueuedPlayer mockPlayer = Mockito.mock(QueuedPlayer.class);
        regular.addPlayer(mockPlayer);
        priority.addPlayer(mockPlayer);
        premium.addPlayer(mockPlayer);

        Set<SubQueue> subQueues = new ConcurrentSkipListSet<>();
        subQueues.add(regular);
        subQueues.add(priority);
        subQueues.add(premium);

        Queue queue = new Queue(subQueues);

        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(premium, queue.getNextSubQueue(false));
            Assertions.assertEquals(premium, queue.getNextSubQueue(false));
            Assertions.assertEquals(premium, queue.getNextSubQueue(false));
            Assertions.assertEquals(priority, queue.getNextSubQueue(false));
            Assertions.assertEquals(regular, queue.getNextSubQueue(false));

            Assertions.assertEquals(premium, queue.getNextSubQueue(false));
            Assertions.assertEquals(premium, queue.getNextSubQueue(false));
            Assertions.assertEquals(premium, queue.getNextSubQueue(false));
            Assertions.assertEquals(priority, queue.getNextSubQueue(false));
            Assertions.assertEquals(regular, queue.getNextSubQueue(false));
        }
    }

    @Test
    void testDrySubQueueOrdering() {
        SubQueue regular = new SubQueue("regular", 0, 1);
        SubQueue priority = new SubQueue("priority", 1, 1);
        SubQueue premium = new SubQueue("premium", 5, 3);

        QueuedPlayer mockPlayer = Mockito.mock(QueuedPlayer.class);
        regular.addPlayer(mockPlayer);
        priority.addPlayer(mockPlayer);
        premium.addPlayer(mockPlayer);

        Set<SubQueue> subQueues = new ConcurrentSkipListSet<>();
        subQueues.add(regular);
        subQueues.add(priority);
        subQueues.add(premium);

        Queue queue = new Queue(subQueues);

        Assertions.assertEquals(premium, queue.getNextSubQueue(true));
        Assertions.assertEquals(premium, queue.getNextSubQueue(true));
        Assertions.assertEquals(premium, queue.getNextSubQueue(true));
        Assertions.assertEquals(premium, queue.getNextSubQueue(true));
        Assertions.assertEquals(premium, queue.getNextSubQueue(true));
    }

    @Test
    void testEmptySubQueueOrdering() {
        SubQueue regular = new SubQueue("regular", 0, 1);
        SubQueue priority = new SubQueue("priority", 1, 1);
        SubQueue premium = new SubQueue("premium", 5, 3);

        Set<SubQueue> subQueues = new ConcurrentSkipListSet<>();
        subQueues.add(regular);
        subQueues.add(priority);
        subQueues.add(premium);

        Queue queue = new Queue(subQueues);

        // The next queue should always be the regular one since all subqueues have no player.
        Assertions.assertEquals(regular, queue.getNextSubQueue(false));
        Assertions.assertEquals(regular, queue.getNextSubQueue(false));
        Assertions.assertEquals(regular, queue.getNextSubQueue(false));
        Assertions.assertEquals(regular, queue.getNextSubQueue(false));
        Assertions.assertEquals(regular, queue.getNextSubQueue(false));

        // Add a player to the priority queue
        QueuedPlayer mockPlayer = Mockito.mock(QueuedPlayer.class);
        priority.addPlayer(mockPlayer);
        Assertions.assertEquals(priority, queue.getNextSubQueue(false));
        Assertions.assertEquals(regular, queue.getNextSubQueue(false));
        Assertions.assertEquals(priority, queue.getNextSubQueue(false));
        Assertions.assertEquals(regular, queue.getNextSubQueue(false));
        Assertions.assertEquals(priority, queue.getNextSubQueue(false));
    }

    @Test
    void singleOptionRatioTest() {
        Ratio<Integer> ratio = new Ratio<>(Map.of(1, 1));

        Assertions.assertEquals(1, ratio.next(false));
        Assertions.assertEquals(1, ratio.next(false));
    }

    @Test
    void multiOptionRatioTest() {
        Map<Integer, Integer> ints = new LinkedHashMap<>();
        ints.put(1, 1);
        ints.put(2, 2);
        ints.put(3, 3);

        Ratio<Integer> ratio = new Ratio<>(ints);

        Assertions.assertEquals(1, ratio.next(false));
        Assertions.assertEquals(2, ratio.next(false));
        Assertions.assertEquals(2, ratio.next(false));
        Assertions.assertEquals(3, ratio.next(false));
        Assertions.assertEquals(3, ratio.next(false));
        Assertions.assertEquals(3, ratio.next(false));

        // Rolls over back to 1
        Assertions.assertEquals(1, ratio.next(false));
    }

    @Test
    void dryRatioTest() {
        Map<Integer, Integer> ints = new LinkedHashMap<>();
        ints.put(1, 1);
        ints.put(2, 2);
        ints.put(3, 3);

        Ratio<Integer> ratio = new Ratio<>(ints);

        Assertions.assertEquals(1, ratio.next(true));
        Assertions.assertEquals(1, ratio.next(true));
        Assertions.assertEquals(1, ratio.next(true));
    }

    @Test
    void testRatioWithDry() {
        Map<Integer, Integer> ints = new LinkedHashMap<>();
        ints.put(1, 1);
        ints.put(2, 2);
        ints.put(3, 3);

        Ratio<Integer> ratio = new Ratio<>(ints);

        Assertions.assertEquals(1, ratio.next(true));
        Assertions.assertEquals(1, ratio.next(false));
        Assertions.assertEquals(2, ratio.next(false));
        Assertions.assertEquals(2, ratio.next(true));
        Assertions.assertEquals(2, ratio.next(true));
        Assertions.assertEquals(2, ratio.next(true));
        Assertions.assertEquals(2, ratio.next(false));
        Assertions.assertEquals(3, ratio.next(false));
        Assertions.assertEquals(3, ratio.next(false));
        Assertions.assertEquals(3, ratio.next(false));
        Assertions.assertEquals(1, ratio.next(false));
    }
}

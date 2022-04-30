package net.earthmc.queue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.junit.jupiter.api.Assertions.*;

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
            assertEquals(premium, queue.getNextSubQueue(false));
            assertEquals(premium, queue.getNextSubQueue(false));
            assertEquals(premium, queue.getNextSubQueue(false));
            assertEquals(priority, queue.getNextSubQueue(false));
            assertEquals(regular, queue.getNextSubQueue(false));

            assertEquals(premium, queue.getNextSubQueue(false));
            assertEquals(premium, queue.getNextSubQueue(false));
            assertEquals(premium, queue.getNextSubQueue(false));
            assertEquals(priority, queue.getNextSubQueue(false));
            assertEquals(regular, queue.getNextSubQueue(false));
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

        for (int i = 0; i < 5; i++)
            assertEquals(premium, queue.getNextSubQueue(true));
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

        // The next queue should always be the regular one since all sub queues have no player.
        for (int i = 0; i < 5; i++)
            assertEquals(regular, queue.getNextSubQueue(false));

        // Add a player to the priority queue
        QueuedPlayer mockPlayer = Mockito.mock(QueuedPlayer.class);
        priority.addPlayer(mockPlayer);

        // Should always return the queue the player is in if there are no other players
        for (int i = 0; i < 5; i++)
            assertEquals(priority, queue.getNextSubQueue(false));

        // Add a new player to the premium queue
        premium.addPlayer(Mockito.mock(QueuedPlayer.class));
        // Premium has a maxSends of 3, so expect 3x premium and then 1x priority.
        assertEquals(premium, queue.getNextSubQueue(false));
        assertEquals(premium, queue.getNextSubQueue(false));
        assertEquals(premium, queue.getNextSubQueue(false));
        assertEquals(priority, queue.getNextSubQueue(false));
        assertEquals(premium, queue.getNextSubQueue(false));
        assertEquals(premium, queue.getNextSubQueue(false));
        assertEquals(premium, queue.getNextSubQueue(false));
        assertEquals(priority, queue.getNextSubQueue(false));
    }

    @Test
    void testGetRegularQueue() {
        SubQueue regular = new SubQueue("regular", 0, 1);
        SubQueue priority = new SubQueue("priority", 1, 1);
        SubQueue premium = new SubQueue("premium", 5, 3);

        Set<SubQueue> subQueues = new ConcurrentSkipListSet<>();
        subQueues.add(regular);
        subQueues.add(priority);
        subQueues.add(premium);

        Queue queue = new Queue(subQueues);
        // getRegularQueue should return the last element in the sub queues set
        assertEquals(queue.getRegularQueue().name(), "regular");
    }
}

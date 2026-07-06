package net.earthmc.queue;

import net.earthmc.queue.config.SubQueueTemplate;
import net.earthmc.queue.impl.local.LocalQueue;
import net.earthmc.queue.impl.local.LocalSubQueue;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class QueueTests {
    private SubQueue regular;
    private SubQueue priority;
    private SubQueue premium;
    private Queue queue;

    @BeforeEach
    void initQueue() {
        this.regular = new LocalSubQueue(null, new SubQueueTemplate("regular", 0, 1));
        this.priority = new LocalSubQueue(null, new SubQueueTemplate("priority", 1, 1));
        this.premium = new LocalSubQueue(null, new SubQueueTemplate("premium", 5, 3));

        List<SubQueue> subQueues = new ArrayList<>();
        subQueues.add(regular);
        subQueues.add(priority);
        subQueues.add(premium);
        Collections.sort(subQueues);

        this.queue = new LocalQueue(subQueues);
    }

    private void fill(int count, SubQueue subQueue) {
        for (int i = 0; i < count; i++) {
            queue.addToQueue(QueuedPlayerMock.newMock(), subQueue);
        }
    }

    @Test
    void testSubQueueOrdering() {
        QueuedPlayer mockPlayer = mock(QueuedPlayer.class);
        regular.addPlayer(mockPlayer);
        priority.addPlayer(mockPlayer);
        premium.addPlayer(mockPlayer);

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
        QueuedPlayer mockPlayer = mock(QueuedPlayer.class);
        regular.addPlayer(mockPlayer);
        priority.addPlayer(mockPlayer);
        premium.addPlayer(mockPlayer);

        for (int i = 0; i < 5; i++)
            assertEquals(premium, queue.getNextSubQueue(true));
    }

    @Test
    void testEmptySubQueueOrdering() {
        // The next queue should always be the regular one since all sub queues have no player.
        for (int i = 0; i < 5; i++)
            assertEquals(regular, queue.getNextSubQueue(false));

        // Add a player to the priority queue
        QueuedPlayer mockPlayer = mock(QueuedPlayer.class);
        priority.addPlayer(mockPlayer);

        // Should always return the queue the player is in if there are no other players
        for (int i = 0; i < 5; i++)
            assertEquals(priority, queue.getNextSubQueue(false));

        // Add a new player to the premium queue
        premium.addPlayer(mock(QueuedPlayer.class));
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
        // getRegularQueue should return the last element in the sub queues set
        assertEquals("regular", queue.getRegularQueue().name());
    }

    @Test
    void testQueueAdd_addedWhenEmpty() {
        for (int expectedPosition = 1; expectedPosition <= 5; expectedPosition++) {
            final SubQueue spy = spy(regular);
            final QueuedPlayer player = QueuedPlayerMock.newMock();

            assertEquals(expectedPosition - 1, queue.addToQueue(player, spy)); // 0 indexed
            assertEquals(expectedPosition, regular.players().size());
            assertEquals(expectedPosition, queue.playerCount());
            verify(spy).addToTail(player);
        }
    }

    @Test
    void testQueueAdd_addedToRememberedPosition() {
        final int size = 10;
        fill(size, regular);

        assertEquals(size, queue.playerCount());

        final QueuedPlayer player = QueuedPlayerMock.newMock();
        final int rememberedPosition = 5;
        queue.rememberPosition(player.uuid(), rememberedPosition);

        assertEquals(rememberedPosition, queue.addToQueue(player, regular));
        assertEquals(rememberedPosition, regular.playerPosition(player));
    }

    @Test
    void testQueueAdd_usesFastPath_WhenRememberedAtStart() {
        fill(3, regular);

        final QueuedPlayer player = QueuedPlayerMock.newMock();
        queue.rememberPosition(player.uuid(), 0);

        final SubQueue spy = spy(regular);
        assertEquals(0, queue.addToQueue(player, spy));
        verify(spy).addToHead(player);
    }

    @Test
    void testQueueAdd_usesFastPath_WhenPriorityIsHigher() {
        fill(3, regular);

        final QueuedPlayer player = QueuedPlayerMock.newMock();
        when(player.priority()).thenReturn(new Priority("higher", 1, Component.empty()));

        final SubQueue spy = spy(regular);
        assertEquals(0, queue.addToQueue(player, spy));
        verify(spy).addToHead(player);
    }
}

package net.earthmc.queue;

import net.earthmc.queue.impl.local.LocalQueuedPlayer;

import java.util.UUID;

import static org.mockito.Mockito.*;

public class QueuedPlayerMock {
    public static LocalQueuedPlayer newMock() {
        final LocalQueuedPlayer mock = mock(LocalQueuedPlayer.class);

        final UUID uuid = UUID.randomUUID();
        when(mock.uuid()).thenReturn(uuid);

        when(mock.name()).thenReturn(uuid.toString());

        when(mock.priority()).thenReturn(Priority.NONE_PRIORITY);

        return mock;
    }
}

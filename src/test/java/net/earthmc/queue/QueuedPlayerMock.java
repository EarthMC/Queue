package net.earthmc.queue;

import java.util.UUID;

import static org.mockito.Mockito.*;

public class QueuedPlayerMock {
    public static QueuedPlayer newMock() {
        final QueuedPlayer mock = mock(QueuedPlayer.class);

        final UUID uuid = UUID.randomUUID();
        when(mock.uuid()).thenReturn(uuid);

        when(mock.name()).thenReturn(uuid.toString());

        when(mock.priority()).thenReturn(QueuedPlayer.NONE_PRIORITY);

        return mock;
    }
}

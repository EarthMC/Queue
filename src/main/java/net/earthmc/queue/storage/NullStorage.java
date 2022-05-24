package net.earthmc.queue.storage;

import net.earthmc.queue.QueuedPlayer;
import org.jetbrains.annotations.NotNull;

public class NullStorage extends Storage {
    @Override
    public void loadPlayer(@NotNull QueuedPlayer player) {

    }

    @Override
    public void savePlayer(@NotNull QueuedPlayer player) {

    }
}

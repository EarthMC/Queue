package net.earthmc.queue.storage;

import net.earthmc.queue.QueuedPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public abstract class Storage {
    abstract public CompletableFuture<Void> loadPlayer(@NotNull QueuedPlayer player);

    abstract public CompletableFuture<Void> savePlayer(@NotNull QueuedPlayer player);

    public void enable() throws Exception {}

    public void disable() {}
}

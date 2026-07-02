package net.earthmc.queue.storage;

import net.earthmc.queue.PlayerData;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class Storage {
    abstract public CompletableFuture<PlayerData> loadPlayer(@NotNull UUID uuid);

    abstract public CompletableFuture<Void> savePlayer(UUID uuid, PlayerData data);

    public void enable() throws Exception {}

    public void disable() {}
}

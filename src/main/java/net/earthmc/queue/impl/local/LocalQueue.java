package net.earthmc.queue.impl.local;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.SubQueue;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LocalQueue extends Queue {
    private final Cache<UUID, Integer> rememberedPlayers = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).build();

    private boolean paused;
    private @Nullable String pauseReason;
    private Instant unpauseTime = Instant.MAX;

    public LocalQueue(RegisteredServer server, QueuePlugin plugin) {
        super(server, plugin, plugin.config().newSubQueues());
    }

    /**
     * Only used for tests
     */
    public LocalQueue(List<SubQueue> subQueues) {
        super(subQueues);
    }

    @Override
    public boolean paused() {
        return this.paused;
    }

    @Override
    public void pause(Instant unpauseTime, @Nullable String reason) {
        this.paused = true;
        this.unpauseTime = unpauseTime;
        this.pauseReason = reason;
    }

    @Override
    public void unpause() {
        this.paused = false;
        this.pauseReason = null;
        this.unpauseTime = Instant.MAX;
    }

    @Override
    public Instant unpauseTime() {
        return this.unpauseTime;
    }

    @Override
    public @Nullable String pauseReason() {
        return this.pauseReason;
    }

    @Override
    public void rememberPosition(UUID playerUUID, int position) {
        this.rememberedPlayers.put(playerUUID, position);
    }

    @Override
    public OptionalInt getRememberedPosition(UUID playerUUID) {
        final Integer position = rememberedPlayers.getIfPresent(playerUUID);
        return position != null ? OptionalInt.of(position) : OptionalInt.empty();
    }

    @Override
    public void forgetPosition(UUID playerUUID) {
        this.rememberedPlayers.invalidate(playerUUID);
    }
}

package net.earthmc.queue.api.mycelium;

import net.earthmc.mycelium.api.serialization.JsonCodec;

import java.time.Instant;

/**
 * @param serverName The server to pause the queue for.
 * @param unpauseTime The time at which the queue will be automatically unpaused, or {@link Instant#MAX} to keep it paused forever.
 * @param reason The pause reason shown to users, can be empty.
 */
public record PauseQueue(String serverName, Instant unpauseTime, String reason) {
    public static final JsonCodec<PauseQueue> CODEC = JsonCodec.simple(PauseQueue.class);
}

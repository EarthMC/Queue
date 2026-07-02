package net.earthmc.queue.api.mycelium;

import net.earthmc.mycelium.api.serialization.JsonCodec;

/**
 * @param serverName The server to unpause the queue for.
 */
public record UnpauseQueue(String serverName) {
    public static final JsonCodec<UnpauseQueue> CODEC = JsonCodec.simple(UnpauseQueue.class);
}

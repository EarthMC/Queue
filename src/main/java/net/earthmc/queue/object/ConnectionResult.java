package net.earthmc.queue.object;

import net.kyori.adventure.text.Component;

public sealed interface ConnectionResult permits ConnectionResult.Resulted, ConnectionResult.FailedWithMessage {
    record Resulted(boolean success) implements ConnectionResult {}

    record FailedWithMessage(Component message) implements ConnectionResult {}
}

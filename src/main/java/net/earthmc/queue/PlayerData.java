package net.earthmc.queue;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public class PlayerData {
    private boolean autoQueueDisabled;
    private String lastJoinedServer;

    private boolean dirty = false;

    public PlayerData(boolean autoQueueDisabled, String lastJoinedServer) {
        this.autoQueueDisabled = autoQueueDisabled;
        this.lastJoinedServer = lastJoinedServer;
    }

    public boolean isAutoQueueDisabled() {
        return autoQueueDisabled;
    }

    public Optional<String> getLastJoinedServer() {
        return Optional.ofNullable(lastJoinedServer);
    }

    public void setAutoQueueDisabled(boolean autoQueueDisabled) {
        dirty |= this.autoQueueDisabled != autoQueueDisabled;
        this.autoQueueDisabled = autoQueueDisabled;
    }

    public void setLastJoinedServer(@Nullable String lastJoinedServer) {
        dirty |= !Objects.equals(this.lastJoinedServer, lastJoinedServer);
        this.lastJoinedServer = lastJoinedServer;
    }

    public boolean isDirty() {
        return dirty;
    }
}

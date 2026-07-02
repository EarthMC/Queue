package net.earthmc.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public abstract class QueueController {
    public abstract Queue createQueue(final RegisteredServer server);

    public abstract QueuedPlayer player(final Player player);

    public void reloadCallback() {}

    public void disable() {}

    public abstract boolean canTickQueues();
}

package net.earthmc.queue.impl.mycelium;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.earthmc.mycelium.api.Mycelium;
import net.earthmc.mycelium.api.messaging.ChannelIdentifier;
import net.earthmc.mycelium.api.network.Server;
import net.earthmc.mycelium.api.serialization.Codecs;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.SubQueue;
import net.earthmc.queue.config.SubQueueTemplate;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

public class RemoteQueue extends Queue {
    private final Mycelium api = Mycelium.api();

    public RemoteQueue(RegisteredServer server, QueuePlugin plugin) {
        super(server, plugin);
    }

    @Override
    public boolean paused() {
        final Long unpauseTime = api.dataStore().get("queue:" + this.getName() + ":paused_until", Codecs.LONG);
        return unpauseTime != null && (unpauseTime == Long.MAX_VALUE || System.currentTimeMillis() < unpauseTime);
    }

    @Override
    public void pause(Instant unpauseTime, @Nullable String reason) {
        final Duration expiration = Duration.between(Instant.now(), unpauseTime);

        long unpauseMillis;
        try {
            unpauseMillis = unpauseTime.toEpochMilli();
        } catch (ArithmeticException e) {
            unpauseMillis = Long.MAX_VALUE;
        }

        if (unpauseTime.equals(Instant.MAX)) {
            api.dataStore().set("queue:" + this.getName() + ":paused_until", Codecs.LONG, unpauseMillis);
        } else {
            api.dataStore().set("queue:" + this.getName() + ":paused_until", Codecs.LONG, unpauseMillis, expiration);
        }

        if (reason == null || reason.isEmpty()) {
            api.dataStore().remove("queue:" + this.getName() + ":paused_reason");
        } else {
            api.dataStore().set("queue:" + this.getName() + ":paused_reason", reason, expiration);
        }
    }

    @Override
    public void unpause() {
        api.dataStore().remove("queue:" + this.getName() + ":paused_until");
        api.dataStore().remove("queue:" + this.getName() + ":paused_reason");
        wakeup();
    }

    @Override
    public Instant unpauseTime() {
        final Long unpauseTime = api.dataStore().get("queue:" + this.getName() + ":paused_until", Codecs.LONG);
        return unpauseTime != null && unpauseTime < Long.MAX_VALUE ? Instant.ofEpochMilli(unpauseTime) : Instant.MAX;
    }

    @Override
    public @Nullable String pauseReason() {
        return api.dataStore().get("queue:" + this.getName() + ":paused_reason", Codecs.STRING);
    }

    @Override
    public void rememberPosition(UUID playerUUID, int position) {
        api.dataStore().set("queue:" + this.getName() + ":remembered_position:" + playerUUID, Codecs.INTEGER, position, Queue.REMEMBERED_POSITION_TIME);
    }

    @Override
    public OptionalInt getRememberedPosition(UUID playerUUID) {
        final Integer position = api.dataStore().get("queue:" + this.getName() + ":remembered_position:" + playerUUID, Codecs.INTEGER);
        return position != null ? OptionalInt.of(position) : OptionalInt.empty();
    }

    @Override
    public void forgetPosition(UUID playerUUID) {
        api.dataStore().remove("queue:" + this.getName() + ":remembered_position:" + playerUUID);
    }

    @Override
    public int connectedPlayerCount() {
        final Server server = api.network().getServerById(getServer().getServerInfo().getName());
        if (server != null) {
            return server.playerCount();
        }

        return getServer().getPlayersConnected().size();
    }

    @Override
    public List<SubQueue> createFromTemplates(List<SubQueueTemplate> templates) {
        final List<SubQueue> ret = new ArrayList<>();

        for (final SubQueueTemplate template : templates) {
            ret.add(new RemoteSubQueue(this, template));
        }

        return ret;
    }

    @Override
    public void wakeup() {
        super.wakeup();

        if (!QueuePlugin.instance().controller().canTickQueues()) {
            api.messaging().message(api.messaging().bind(ChannelIdentifier.identifier("queue:wakeup"), Codecs.STRING), this.getName()).send();
        }
    }
}

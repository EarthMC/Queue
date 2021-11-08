package net.earthmc.queue;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public class QueuedPlayer {
    private final Player player;
    private Queue queue;
    private Priority priority;

    public QueuedPlayer(Player player) {
        this.player = player;
    }

    public QueuedPlayer(Player player, Queue queue) {
        this.player = player;
        queue.enqueue(this);
    }

    public Player player() {
        return player;
    }

    public Priority priority() {
        if (priority == null)
            priority = calculatePriority();

        return priority;
    }

    public boolean priorityQueue() {
        return priority().weight > 0;
    }

    public int position() {
        if (queue == null)
            return -1;

        return queue.getQueue(this).players.indexOf(this);
    }

    public boolean isInQueue() {
        if (this.queue != null)
            if (!this.queue.hasPlayer(this))
                this.queue = null;

        return this.queue != null;
    }

    public Queue queue() {
        return this.queue;
    }

    public void queue(Queue queue) {
        this.queue = queue;
    }

    @Contract(" -> new")
    private @NotNull Priority calculatePriority() {
        if (player.hasPermission("queue.priority.staff"))
            return new Priority(6, text("Staff", DARK_GREEN).append(text(" access activated.", GREEN)));
        else if (player.hasPermission("queue.priority.premium"))
            return new Priority(5, text("Premium", LIGHT_PURPLE).append(text(" access activated.", GREEN)));
        else if (player.hasPermission("queue.priority.donator3"))
            return new Priority(4, text("Blue", BLUE).append(text(" donator access activated.", GREEN)));
        else if (player.hasPermission("queue.priority.donator2"))
            return new Priority(3, text("Purple", DARK_PURPLE).append(text(" donator access activated.", GREEN)));
        else if (player.hasPermission("queue.priority.donator"))
            return new Priority(2, text("Yellow", YELLOW).append(text(" donator access activated.", GREEN)));
        else if (player.hasPermission("queue.priority.priority"))
            return new Priority(1, text("Priority access activated.", GREEN));
        else
            return new Priority(0, empty());
    }

    public record Priority(int weight, Component message) {
        public boolean premium() {
            return weight >= 5;
        }

        public boolean priority() {
            return weight < 4 && weight > 0;
        }

        public boolean regular() {
            return weight == 0;
        }

        public Queue.SubQueueType queueType() {
            return switch (weight) {
                case 1, 2, 3, 4 -> Queue.SubQueueType.PRIORITY;
                case 5, 6 -> Queue.SubQueueType.PREMIUM;
                default -> Queue.SubQueueType.REGULAR;
            };
        }
    }
}

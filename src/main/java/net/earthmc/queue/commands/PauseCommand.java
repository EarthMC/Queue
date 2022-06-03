package net.earthmc.queue.commands;

import com.google.common.primitives.Ints;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class PauseCommand extends BaseCommand implements SimpleCommand {

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!source.hasPermission("queue.pause")) {
            source.sendMessage(Component.text("You do not have enough permissions to use this command.", NamedTextColor.RED));
            return;
        }

        if (invocation.arguments().length == 0) {
            source.sendMessage(Component.text("Not enough arguments. Usage: /pausequeue [queue] [seconds].", NamedTextColor.RED));
            return;
        }

        Queue queue = QueuePlugin.instance().queue(invocation.arguments()[0]);
        if (queue == null) {
            source.sendMessage(Component.text(invocation.arguments()[0] + " is not a valid queue.", NamedTextColor.RED));
            return;
        }

        Instant unpauseTime = Instant.MAX;
        boolean usingSeconds = false;
        Integer seconds = 0;

        if (invocation.arguments().length > 1) {
            seconds = Ints.tryParse(invocation.arguments()[1]);

            usingSeconds = seconds != null;
            if (seconds != null)
                unpauseTime = Instant.now().plusSeconds(seconds);
        }

        if (queue.paused())
            queue.pause(false);
        else
            queue.pause(true, unpauseTime);

        String message = String.format("You have %s the queue for server %s.", queue.paused() ? "paused" : "resumed", invocation.arguments()[0]);
        if (usingSeconds && queue.paused())
            message += " for " + seconds + " seconds.";

        source.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return Collections.emptyList();
    }
}

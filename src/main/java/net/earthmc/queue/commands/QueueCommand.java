package net.earthmc.queue.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

public class QueueCommand extends BaseCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            if (!(invocation.source() instanceof Player player) || !QueuePlugin.instance().queued(player).isInQueue()) {
                invocation.source().sendMessage(Component.text("You are not in a queue.", NamedTextColor.RED));
                return;
            }

            QueuedPlayer queuedPlayer = QueuePlugin.instance().queued(player);
            player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(queuedPlayer.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(queuedPlayer.queue().getQueue(queuedPlayer).size(), NamedTextColor.GREEN).append(Component.text(" for " + queuedPlayer.queue().getServerFormatted(), NamedTextColor.YELLOW))))));
            if (queuedPlayer.queue().paused())
                player.sendMessage(Component.text("The queue you are currently in is paused.", NamedTextColor.GRAY));
        } else {
            if (args[0].equalsIgnoreCase("skip")) {
                if (!invocation.source().hasPermission("queue.skip"))
                    return;

                if (args.length < 2) {
                    invocation.source().sendMessage(Component.text("Not enough arguments! Usage: /queue skip [player].", NamedTextColor.RED));
                    return;
                }

                Optional<Player> optPlayer = QueuePlugin.proxy().getPlayer(args[1]);
                if (optPlayer.isEmpty()) {
                    invocation.source().sendMessage(Component.text(args[1] + " is currently offline or doesn't exist.", NamedTextColor.RED));
                    return;
                }

                Player player = optPlayer.get();
                QueuedPlayer queuedPlayer = QueuePlugin.instance().queued(player);
                if (!queuedPlayer.isInQueue()) {
                    invocation.source().sendMessage(Component.text(player.getUsername() + " isn't in a queue.", NamedTextColor.RED));
                    return;
                }

                Queue queue = queuedPlayer.queue();
                if (queue.paused()) {
                    invocation.source().sendMessage(Component.text("The queue " + player.getUsername() + " is in is currently paused.", NamedTextColor.RED));
                    return;
                }

                queue.remove(queuedPlayer);
                queuedPlayer.queue(null);

                player.createConnectionRequest(queue.getServer()).connect().thenAccept(result -> {
                    if (result.isSuccessful()) {
                        player.sendMessage(Component.text("You have been sent to " + queue.getServerFormatted() + ".", NamedTextColor.GREEN));
                        invocation.source().sendMessage(Component.text(player.getUsername() + " has been sent to " + queue.getServerFormatted() + ".", NamedTextColor.GREEN));
                    }
                });
            }
        }
    }
}

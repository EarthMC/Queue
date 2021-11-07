package net.earthmc.queue.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class QueueCommand extends BaseCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player) || !QueuePlugin.instance().queued(player).isInQueue()) {
            invocation.source().sendMessage(Component.text("You are not in a queue.", NamedTextColor.RED));
            return;
        }

        QueuedPlayer queuedPlayer = QueuePlugin.instance().queued(player);
        player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(queuedPlayer.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(queuedPlayer.queue().getQueue(queuedPlayer).size(), NamedTextColor.GREEN).append(Component.text(" for " + queuedPlayer.queue().getServerFormatted(), NamedTextColor.YELLOW))))));
        if (queuedPlayer.queue().paused())
            player.sendMessage(Component.text("The queue you are currently in is paused.", NamedTextColor.GRAY));
    }
}

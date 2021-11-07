package net.earthmc.queue.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collections;
import java.util.List;

public class LeaveCommand extends BaseCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("This command cannot be used by console.", NamedTextColor.RED));
            return;
        }

        QueuedPlayer queuedPlayer = QueuePlugin.instance().queued(player);

        if (!queuedPlayer.isInQueue()) {
            player.sendMessage(Component.text("You are not in a queue.", NamedTextColor.RED));
            return;
        }

        queuedPlayer.queue().remove(queuedPlayer);
        player.sendMessage(Component.text("You have left the queue.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}

package net.earthmc.queue.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class LeaveCommand {

    private LeaveCommand() {}

    public static BrigadierCommand createCommand(final QueuePlugin plugin) {
        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("leavequeue")
            .requires(source -> source instanceof Player)
            .executes(ctx -> {
                if (!(ctx.getSource() instanceof Player player)) {
                    return Command.SINGLE_SUCCESS;
                }

                final QueuedPlayer queuedPlayer = plugin.queued(player);

                if (!queuedPlayer.isInQueue()) {
                    player.sendMessage(Component.text("You are not in a queue.", NamedTextColor.RED));
                    return Command.SINGLE_SUCCESS;
                }

                queuedPlayer.queue().remove(queuedPlayer);
                queuedPlayer.queue(null);
                player.sendMessage(Component.text("You have left the queue.", NamedTextColor.GREEN));
                return Command.SINGLE_SUCCESS;
            })
            .build();

        return new BrigadierCommand(node);
    }
}

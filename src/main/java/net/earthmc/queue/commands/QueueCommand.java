package net.earthmc.queue.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class QueueCommand {

    private QueueCommand() {}

    public static BrigadierCommand createCommand(final QueuePlugin plugin) {
        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("queue")
            .executes(QueueCommand::sendPosition)
            .then(BrigadierCommand.literalArgumentBuilder("reload")
                .requires(source -> source.hasPermission("queue.reload"))
                .executes(ctx -> {
                    if (!plugin.reload()) {
                        ctx.getSource().sendMessage(Component.text("Couldn't reload the config, check the console for details", NamedTextColor.RED));
                    } else {
                        ctx.getSource().sendMessage(Component.text("Successfully reloaded the config.", NamedTextColor.GREEN));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
            .then(BrigadierCommand.literalArgumentBuilder("position")
                .requires(source -> source instanceof Player)
                .executes(QueueCommand::sendPosition))
            .then(BrigadierCommand.literalArgumentBuilder("auto")
                .requires(source -> source instanceof Player)
                .executes(ctx -> {
                    if (!(ctx.getSource() instanceof Player player)) {
                        return Command.SINGLE_SUCCESS;
                    }

                    final QueuedPlayer queuedPlayer = plugin.queued(player);

                    queuedPlayer.setAutoQueueDisabled(!queuedPlayer.isAutoQueueDisabled());
                    if (queuedPlayer.isAutoQueueDisabled()) {
                        player.sendMessage(Component.text("You will no longer automatically join a queue after joining.", NamedTextColor.GREEN));
                        plugin.cancelAutoQueueTask(player);
                    } else {
                        player.sendMessage(Component.text("You will now automatically join the queue for your last server upon joining.", NamedTextColor.GREEN));
                    }

                    return Command.SINGLE_SUCCESS;
                }))
            .then(BrigadierCommand.literalArgumentBuilder("skip")
                  .requires(source -> source.hasPermission("queue.skip"))
                  .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.string())
                       .suggests(Brig::suggestOnlinePlayers)
                       .executes(ctx -> {
                           final String playerName = ctx.getArgument("player", String.class);
                           final Player player = plugin.proxy().getPlayer(playerName).orElse(null);
                           if (player == null) {
                               ctx.getSource().sendMessage(Component.text(playerName + " is currently offline or doesn't exist.", NamedTextColor.RED));
                               return 0;
                           }

                           QueuedPlayer queuedPlayer = plugin.queued(player);
                           if (!queuedPlayer.isInQueue()) {
                               ctx.getSource().sendMessage(Component.text(player.getUsername() + " isn't in a queue.", NamedTextColor.RED));
                               return 0;
                           }

                           Queue queue = queuedPlayer.queue();
                           if (queue.paused()) {
                               ctx.getSource().sendMessage(Component.text("The queue " + player.getUsername() + " is in is currently paused.", NamedTextColor.RED));
                               return 0;
                           }

                           queue.remove(queuedPlayer);
                           queuedPlayer.queue(null);

                           player.createConnectionRequest(queue.getServer()).connect().thenAccept(result -> {
                               if (result.isSuccessful()) {
                                   player.sendMessage(Component.text("You have been sent to " + queue.getServerFormatted() + ".", NamedTextColor.GREEN));
                                   ctx.getSource().sendMessage(Component.text(player.getUsername() + " has been sent to " + queue.getServerFormatted() + ".", NamedTextColor.GREEN));
                               }
                           });

                           return Command.SINGLE_SUCCESS;
                       })))
            .then(BrigadierCommand.literalArgumentBuilder("forget")
                .requires(source -> source.hasPermission("queue.forget"))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.string())
                    .suggests(Brig::suggestOnlinePlayers)
                    .executes(ctx -> {
                        final String playerName = ctx.getArgument("player", String.class);
                        final Player player = plugin.proxy().getPlayer(playerName).orElse(null);
                        if (player == null) {
                            ctx.getSource().sendMessage(Component.text(playerName + " is currently offline or doesn't exist.", NamedTextColor.RED));
                            return 0;
                        }

                        for (Queue queue : plugin.queues().values()) {
                            queue.forgetPosition(player.getUniqueId());
                        }

                        ctx.getSource().sendMessage(Component.text(player.getUsername() + "'s position has been forgotten in all queues.", NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(BrigadierCommand.literalArgumentBuilder("remove")
                .requires(source -> source.hasPermission("queue.remove"))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.string())
                    .suggests(Brig::suggestOnlinePlayers)
                    .executes(ctx -> {
                        final String playerName = ctx.getArgument("player", String.class);
                        final Player player = plugin.proxy().getPlayer(playerName).orElse(null);
                        if (player == null) {
                            ctx.getSource().sendMessage(Component.text(playerName + " is currently offline or doesn't exist.", NamedTextColor.RED));
                            return 0;
                        }

                        final QueuedPlayer queuedPlayer = plugin.queued(player);

                        if (queuedPlayer.isInQueue()) {
                            Queue queue = queuedPlayer.queue();
                            queue.remove(queuedPlayer);
                            ctx.getSource().sendMessage(Component.text("Successfully removed " + queuedPlayer.name() + " from the queue for server " + queue.getServerFormatted() + ".", NamedTextColor.GREEN));
                        } else {
                            ctx.getSource().sendMessage(Component.text(queuedPlayer.name() + " is not in a queue.", NamedTextColor.RED));
                        }
                        return Command.SINGLE_SUCCESS;
                    })))
            .build();

        return new BrigadierCommand(node);
    }

    private static int sendPosition(final CommandContext<CommandSource> ctx) {
        final QueuedPlayer queuedPlayer;
        if (!(ctx.getSource() instanceof Player player) || !(queuedPlayer = QueuePlugin.instance().queued(player)).isInQueue()) {
            ctx.getSource().sendMessage(Component.text("You are not in a queue.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(queuedPlayer.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(queuedPlayer.queue().getSubQueue(queuedPlayer).players().size(), NamedTextColor.GREEN).append(Component.text(" for " + queuedPlayer.queue().getServerFormatted(), NamedTextColor.YELLOW))))));
        if (queuedPlayer.queue().paused()) {
            queuedPlayer.queue().sendPausedQueueMessage(queuedPlayer);
        }

        return Command.SINGLE_SUCCESS;
    }
}

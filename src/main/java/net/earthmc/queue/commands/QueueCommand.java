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
import net.earthmc.queue.SubQueue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
            .then(BrigadierCommand.literalArgumentBuilder("list")
                .requires(source -> source.hasPermission("queue.list"))
                .then(BrigadierCommand.requiredArgumentBuilder("queue", StringArgumentType.string())
                    .suggests(Brig::suggestServers)
                    .executes(QueueCommand::sendTotalQueueSize)
                    .then(BrigadierCommand.requiredArgumentBuilder("subqueue", StringArgumentType.string())
                        .suggests((ctx, builder) -> Brig.filterByStart(ctx, builder, plugin.config().subQueueNames()))
                        .executes(ctx -> sendQueueList(ctx, ctx.getArgument("subqueue", String.class))))
                    .then(BrigadierCommand.literalArgumentBuilder("all")
                        .executes(ctx -> sendQueueList(ctx, null)))
                ))
            .build();

        return new BrigadierCommand(node);
    }

    private static int sendPosition(final CommandContext<CommandSource> ctx) {
        final QueuedPlayer queuedPlayer;
        if (!(ctx.getSource() instanceof Player player) || !(queuedPlayer = QueuePlugin.instance().queued(player)).isInQueue()) {
            ctx.getSource().sendMessage(Component.text("You are not in a queue.", NamedTextColor.RED));
            return 0;
        }

        player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(queuedPlayer.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(queuedPlayer.queue().getSubQueue(queuedPlayer).players().size(), NamedTextColor.GREEN).append(Component.text(" for " + queuedPlayer.queue().getServerFormatted(), NamedTextColor.YELLOW))))));
        if (queuedPlayer.queue().paused()) {
            queuedPlayer.queue().sendPausedQueueMessage(queuedPlayer);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int sendTotalQueueSize(final CommandContext<CommandSource> ctx) {
        final Queue queue = validateQueue(ctx);
        if (queue == null) {
            return 0;
        }

        int playerCount = 0;

        final List<Component> subQueueComponents = new ArrayList<>();
        for (final SubQueue subQueue : queue.getSubQueues()) {
            final int subQueuePlayers = subQueue.players().size();
            playerCount += subQueuePlayers;

            subQueueComponents.add(Component.text(subQueue.name() + ": " + subQueuePlayers, NamedTextColor.GREEN));
        }

        ctx.getSource().sendMessage(Component.text("There are currently ", NamedTextColor.YELLOW).append(Component.text(playerCount, NamedTextColor.GREEN)).append(Component.text(" player" + (playerCount == 1 ? "" : "s") + " in the queue for " + queue.getServerFormatted() + ".")));
        ctx.getSource().sendMessage(Component.text("Subqueue breakdown: ", NamedTextColor.YELLOW).append(Component.join(JoinConfiguration.spaces(), subQueueComponents)));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendQueueList(final CommandContext<CommandSource> ctx, final @Nullable String subQueueName) {
        final Queue queue = validateQueue(ctx);
        if (queue == null) {
            return 0;
        }

        int successCount = 0;
        for (final SubQueue subQueue : queue.getSubQueues()) {
            if (subQueueName == null || subQueue.name().equalsIgnoreCase(subQueueName)) {
                successCount++;
                sendQueueList(ctx, queue, subQueue);
            }
        }

        if (successCount == 0) {
            ctx.getSource().sendMessage(Component.text("Could not find any subqueues with name '" + subQueueName + "'.", NamedTextColor.RED));
        }

        return successCount;
    }

    private static void sendQueueList(final CommandContext<CommandSource> ctx, final Queue queue, final SubQueue subQueue) {
        final List<String> playerNames = new ArrayList<>();
        for (final QueuedPlayer player : subQueue.players()) {
            playerNames.add(player.name());
        }

        ctx.getSource().sendMessage(Component.text("Queue for " + queue.getServerFormatted() + " (" + subQueue.name() + ") [" + playerNames.size() + "]: ", NamedTextColor.YELLOW).append(Component.text(String.join(", ", playerNames), NamedTextColor.GREEN)));
    }

    private static @Nullable Queue validateQueue(final CommandContext<CommandSource> ctx) {
        final String queueName = ctx.getArgument("queue", String.class);
        if (!Brig.hasPrefixedPermission(ctx.getSource(), "queue.join.", queueName)) {
            ctx.getSource().sendMessage(Component.text(queueName + " is not a valid server.", NamedTextColor.RED));
            return null;
        }

        final Queue queue = QueuePlugin.instance().queue(queueName);
        if (queue == null) {
            ctx.getSource().sendMessage(Component.text(queueName + " is not a valid server.", NamedTextColor.RED));
        }

        return queue;
    }
}

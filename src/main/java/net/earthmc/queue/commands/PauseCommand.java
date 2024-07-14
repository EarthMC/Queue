package net.earthmc.queue.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.Instant;

public class PauseCommand {

    public static BrigadierCommand createCommand(final QueuePlugin plugin) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("pausequeue")
                .requires(source -> source.hasPermission("queue.pause"))
                .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            plugin.queues().keySet().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(BrigadierCommand.requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(context -> pause(context, context.getArgument("reason", String.class)))
                        )
                        .executes(context -> pause(context, null)))
                .build();

        return new BrigadierCommand(node);
    }

    public static int pause(final CommandContext<CommandSource> context, String reason) {
        String server = context.getArgument("server", String.class);
        Queue queue = QueuePlugin.instance().queue(server);
        if (queue == null) {
            context.getSource().sendMessage(Component.text(server + " is not a valid server.", NamedTextColor.RED));
            return 0;
        }

        queue.pause(!queue.paused(), Instant.MAX, reason);
        String message = String.format("You have %s the queue for server %s", queue.paused() ? "paused" : "resumed", server);
        if (reason != null)
            message += " with the reason '" + reason + "'.";

        context.getSource().sendMessage(Component.text(message, NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }
}

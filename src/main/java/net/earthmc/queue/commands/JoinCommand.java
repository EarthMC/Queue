package net.earthmc.queue.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Locale;

public class JoinCommand {
    private JoinCommand() {}

    public static BrigadierCommand createCommand(final QueuePlugin plugin) {
        final LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("leavequeue")
            .requires(source -> source instanceof Player)
            .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.string())
                 .suggests((ctx, builder) -> {
                     final List<String> allServers = plugin.proxy().getAllServers().stream().map(server -> server.getServerInfo().getName().toLowerCase(Locale.ROOT)).toList();
                     return Brig.filterByPermission(ctx, builder, allServers, "queue.join.");
                 })
                 .executes(ctx -> {
                     if (!(ctx.getSource() instanceof Player player)) {
                         return Command.SINGLE_SUCCESS;
                     }

                     final String server = ctx.getArgument("server", String.class);
                     if (!Brig.hasPrefixedPermission(player, "queue.join.", server)) {
                         player.sendMessage(Component.text(server + " is not a valid server.", NamedTextColor.RED));
                         return 0;
                     }

                     if (player.getCurrentServer().map(currentServer -> currentServer.getServerInfo().getName().equalsIgnoreCase(server)).orElse(false)) {
                         player.sendMessage(Component.text("You are already connected to this server.", NamedTextColor.RED));
                         return 0;
                     }

                     final Queue queue = plugin.queue(server);
                     if (queue == null) {
                         player.sendMessage(Component.text(server + " is not a valid server.", NamedTextColor.RED));
                         return 0;
                     }

                     plugin.cancelAutoQueueTask(player);
                     queue.enqueue(plugin.queued(player));

                     return Command.SINGLE_SUCCESS;
                 }))
            .build();

        return new BrigadierCommand(node);
    }
}

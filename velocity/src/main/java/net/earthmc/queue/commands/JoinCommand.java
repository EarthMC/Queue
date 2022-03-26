package net.earthmc.queue.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class JoinCommand extends BaseCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("This command cannot be used by console.", NamedTextColor.RED));
            return;
        }

        if (invocation.arguments().length == 0) {
            source.sendMessage(Component.text("Not enough arguments. Usage: /joinqueue [server].", NamedTextColor.RED));
            return;
        }

        String server = invocation.arguments()[0];
        if (!invocation.source().hasPermission("queue.join." + server.toLowerCase()) && !invocation.source().hasPermission("queue.join.*")) {
            source.sendMessage(Component.text(server + " is not a valid server.", NamedTextColor.RED));
            return;
        }

        if (player.getCurrentServer().isPresent() && player.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase(server)) {
            player.sendMessage(Component.text("You are already connected to this server.", NamedTextColor.RED));
            return;
        }

        Queue queue = QueuePlugin.instance().queue(server);
        if (queue == null) {
            player.sendMessage(Component.text(server + " is not a valid server.", NamedTextColor.RED));
            return;
        }

        boolean confirmation = invocation.arguments().length >= 2 && invocation.arguments()[1].equalsIgnoreCase("confirm");

        // Remove autoqueue for this player
        QueuePlugin.instance().removeAutoQueue(player);

        queue.enqueue(QueuePlugin.instance().queued(player), confirmation);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> servers = QueuePlugin.proxy().getAllServers().stream().map(server -> server.getServerInfo().getName().toLowerCase(Locale.ENGLISH)).toList();

            return filterByPermission(invocation, servers, "queue.join.");
        });
    }
}

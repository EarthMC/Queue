package net.earthmc.queue.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class JoinCommand extends BaseCommand implements SimpleCommand {
    private final QueuePlugin plugin;

    public JoinCommand(@NotNull QueuePlugin plugin) {
        this.plugin = plugin;
    }

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
        if (!hasPrefixedPermission(invocation.source(), "queue.join.", server)) {
            source.sendMessage(Component.text(server + " is not a valid server.", NamedTextColor.RED));
            return;
        }

        if (player.getCurrentServer().map(currentServer -> currentServer.getServerInfo().getName().equalsIgnoreCase(server)).orElse(false)) {
            player.sendMessage(Component.text("You are already connected to this server.", NamedTextColor.RED));
            return;
        }

        Queue queue = plugin.queue(server);
        if (queue == null) {
            player.sendMessage(Component.text(server + " is not a valid server.", NamedTextColor.RED));
            return;
        }

        boolean confirmation = invocation.arguments().length >= 2 && invocation.arguments()[1].equalsIgnoreCase("confirm");

        // Remove auto queue for this player
        plugin.removeAutoQueue(player);

        queue.enqueue(plugin.queued(player), confirmation);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> servers = QueuePlugin.instance().proxy().getAllServers().stream().map(server -> server.getServerInfo().getName().toLowerCase(Locale.ROOT)).toList();

            if (invocation.arguments().length <= 1)
                return filterByPermission(invocation.source(), servers, "queue.join.", invocation.arguments().length == 0 ? null : invocation.arguments()[0]);
            else
                return Collections.emptyList();
        });
    }
}

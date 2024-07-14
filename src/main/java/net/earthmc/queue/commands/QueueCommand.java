package net.earthmc.queue.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class QueueCommand extends BaseCommand implements SimpleCommand {

    private static final List<String> tabCompletes = Arrays.asList("reload", "skip", "auto", "position", "remove");

    private final QueuePlugin plugin;

    public QueueCommand(@NotNull QueuePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        final String[] args = invocation.arguments();
        switch (args.length) {
            case 0:
            case 1:
                return filterByPermission(invocation.source(), tabCompletes, "queue.", args.length > 0 ? args[0] : null);
            case 2: {
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "skip":
                    case "forget":
                    case "remove":
                        if (hasPrefixedPermission(invocation.source(), "queue.", args[0]))
                            return filterByStart(plugin.proxy().getAllPlayers().stream().map(Player::getUsername).toList(), args[1]);
                        break;
                }
            }
        }

        return Collections.emptyList();
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0 || (invocation.arguments().length > 0 && invocation.arguments()[0].equalsIgnoreCase("position"))) {
            if (!(invocation.source() instanceof Player player) || !QueuePlugin.instance().queued(player).isInQueue()) {
                invocation.source().sendMessage(Component.text("You are not in a queue.", NamedTextColor.RED));
                return;
            }

            QueuedPlayer queuedPlayer = QueuePlugin.instance().queued(player);
            player.sendMessage(Component.text("You are currently in position ", NamedTextColor.YELLOW).append(Component.text(queuedPlayer.position() + 1, NamedTextColor.GREEN).append(Component.text(" of ", NamedTextColor.YELLOW).append(Component.text(queuedPlayer.queue().getSubQueue(queuedPlayer).players().size(), NamedTextColor.GREEN).append(Component.text(" for " + queuedPlayer.queue().getServerFormatted(), NamedTextColor.YELLOW))))));
            if (queuedPlayer.queue().paused()) {
                queuedPlayer.queue().sendPausedQueueMessage(queuedPlayer);
            }

            return;
        }

        if (!hasPrefixedPermission(invocation.source(), "queue.", args[0])) {
            invocation.source().sendMessage(Component.text("You do not have enough permission to use this command.", NamedTextColor.RED));
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "skip" -> parseQueueSkip(invocation);
            case "reload" -> parseQueueReload(invocation);
            case "auto" -> parseQueueAutoQueue(invocation);
            case "forget" -> parseQueueForget(invocation);
            case "remove" -> parseQueueRemove(invocation);
            default -> invocation.source().sendMessage(Component.text(invocation.arguments()[0] + " is not a valid subcommand.", NamedTextColor.RED));
        }
    }

    private void parseQueueSkip(Invocation invocation) {
        if (!invocation.source().hasPermission("queue.skip"))
            return;

        if (invocation.arguments().length < 2) {
            invocation.source().sendMessage(Component.text("Not enough arguments! Usage: /queue skip [player].", NamedTextColor.RED));
            return;
        }

        Optional<Player> optPlayer = plugin.proxy().getPlayer(invocation.arguments()[1]);
        if (optPlayer.isEmpty()) {
            invocation.source().sendMessage(Component.text(invocation.arguments()[1] + " is currently offline or doesn't exist.", NamedTextColor.RED));
            return;
        }

        Player player = optPlayer.get();
        QueuedPlayer queuedPlayer = plugin.queued(player);
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

    private void parseQueueReload(Invocation invocation) {
        if (!plugin.reload())
            invocation.source().sendMessage(Component.text("Couldn't reload the config, check the console for details", NamedTextColor.RED));
        else
            invocation.source().sendMessage(Component.text("Successfully reloaded the config.", NamedTextColor.GREEN));
    }

    private void parseQueueAutoQueue(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("This command cannot be used by console.", NamedTextColor.RED));
            return;
        }

        QueuedPlayer queuedPlayer = plugin.queued(player);

        queuedPlayer.setAutoQueueDisabled(!queuedPlayer.isAutoQueueDisabled());
        if (queuedPlayer.isAutoQueueDisabled())
            player.sendMessage(Component.text("You will no longer automatically join a queue after joining.", NamedTextColor.GREEN));
        else
            player.sendMessage(Component.text("You will now automatically join the queue for your last server upon joining.", NamedTextColor.GREEN));

        // Remove the auto queue task for this player if they've got any
        if (queuedPlayer.isAutoQueueDisabled())
            plugin.removeAutoQueue(player);
    }

    private void parseQueueForget(Invocation invocation) {
        if (!invocation.source().hasPermission("queue.forget"))
            return;

        if (invocation.arguments().length < 2) {
            invocation.source().sendMessage(Component.text("Invalid usage! Usage: /queue forget <player>", NamedTextColor.RED));
            return;
        }

        Optional<Player> optPlayer = plugin.proxy().getPlayer(invocation.arguments()[1]);
        if (optPlayer.isEmpty()) {
            invocation.source().sendMessage(Component.text(invocation.arguments()[1] + " is currently offline or doesn't exist.", NamedTextColor.RED));
            return;
        }

        Player player = optPlayer.get();
        for (Queue queue : plugin.queues().values()) {
            queue.forget(player.getUniqueId());
        }

        invocation.source().sendMessage(Component.text(player.getUsername() + "'s position has been forgotten in all queues.", NamedTextColor.GREEN));
    }

    private void parseQueueRemove(Invocation invocation) {
        if (!invocation.source().hasPermission("queue.remove"))
            return;

        if (invocation.arguments().length < 2) {
            invocation.source().sendMessage(Component.text("Invalid usage! Usage: /queue remove <player>"));
            return;
        }

        Optional<Player> optPlayer = plugin.proxy().getPlayer(invocation.arguments()[1]);
        if (optPlayer.isEmpty()) {
            invocation.source().sendMessage(Component.text(invocation.arguments()[1] + " is currently offline or doesn't exist.", NamedTextColor.RED));
            return;
        }

        QueuedPlayer player = plugin.queued(optPlayer.get());

        if (player.isInQueue()) {
            Queue queue = player.queue();
            queue.remove(player);
            invocation.source().sendMessage(Component.text("Successfully removed " + player.name() + " from the queue for server " + queue.getServerFormatted() + ".", NamedTextColor.GREEN));
        } else
            invocation.source().sendMessage(Component.text(player.name() + " is not in a queue.", NamedTextColor.RED));
    }
}

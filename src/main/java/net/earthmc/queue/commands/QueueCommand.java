package net.earthmc.queue.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.Queue;
import net.earthmc.queue.QueuePlugin;
import net.earthmc.queue.QueuedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class QueueCommand extends BaseCommand implements SimpleCommand {

    private static final List<String> tabCompletes = Arrays.asList("reload", "skip", "autoqueue", "position");

    private final QueuePlugin plugin;

    public QueueCommand(@NotNull QueuePlugin plugin) {
        this.plugin = plugin;
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
            if (queuedPlayer.queue().paused())
                player.sendMessage(Component.text("The queue you are currently in is paused.", NamedTextColor.GRAY));

            return;
        }

        if (!hasPrefixedPermission(invocation.source(), "queue.", args[0])) {
            invocation.source().sendMessage(Component.text("You do not have enough permission to use this command.", NamedTextColor.RED));
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "skip" -> parseQueueSkip(invocation);
            case "reload" -> parseQueueReload(invocation);
            case "autoqueue" -> parseQueueAutoQueue(invocation);
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

        Optional<Player> optPlayer = QueuePlugin.instance().proxy().getPlayer(invocation.arguments()[1]);
        if (optPlayer.isEmpty()) {
            invocation.source().sendMessage(Component.text(invocation.arguments()[1] + " is currently offline or doesn't exist.", NamedTextColor.RED));
            return;
        }

        Player player = optPlayer.get();
        QueuedPlayer queuedPlayer = QueuePlugin.instance().queued(player);
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
        player.sendMessage(Component.text("Autoqueue is now ", NamedTextColor.GRAY).append(queuedPlayer.isAutoQueueDisabled()
                        ? Component.text("disabled", NamedTextColor.RED)
                        : Component.text("enabled", NamedTextColor.GREEN))
                .append(Component.text(".", NamedTextColor.GRAY)));

        // Remove the auto queue task for this player if they've got any
        if (queuedPlayer.isAutoQueueDisabled())
            plugin.removeAutoQueue(player);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        switch (invocation.arguments().length) {
            case 0:
            case 1:
                return filterByPermission(invocation.source(), tabCompletes, "queue.", invocation.arguments().length > 0 ? invocation.arguments()[0] : null);
            case 2: {
                if (invocation.arguments()[0].equalsIgnoreCase("skip") && hasPrefixedPermission(invocation.source(), "queue.", "skip"))
                    return filterByStart(plugin.proxy().getAllPlayers().stream().map(Player::getUsername).toList(), invocation.arguments()[1]);
            }
        }

        return Collections.emptyList();
    }
}

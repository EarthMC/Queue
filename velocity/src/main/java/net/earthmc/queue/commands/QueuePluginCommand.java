package net.earthmc.queue.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.earthmc.queue.QueuePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class QueuePluginCommand extends BaseCommand implements SimpleCommand {
    private final QueuePlugin plugin;
    private final List<String> arguments = Arrays.asList("reload", "info");

    public QueuePluginCommand(QueuePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation.arguments().length == 0 || !arguments.contains(invocation.arguments()[0].toLowerCase(Locale.ROOT))) {
            invocation.source().sendMessage(Component.text("Invalid usage! Usage: [" + String.join("|", arguments) + "]", NamedTextColor.RED));
            return;
        }

        String arg = invocation.arguments()[0].toLowerCase(Locale.ROOT);
        if (!invocation.source().hasPermission("queue." + arg) && !invocation.source().hasPermission("queue.*")) {
            invocation.source().sendMessage(Component.text("You do not have enough permissions to use this command.", NamedTextColor.RED));
            return;
        }

        switch (arg) {
            case "reload" -> {
                if (!plugin.config().reload())
                    invocation.source().sendMessage(Component.text("Couldn't reload the config, check the console for details", NamedTextColor.RED));
                else
                    invocation.source().sendMessage(Component.text("Successfully reloaded the config.", NamedTextColor.GREEN));
            }
            case "info" -> parseQueueInfo(invocation.source());
        }
    }

    private void parseQueueInfo(CommandSource source) {
        source.sendMessage(Component.text("Not yet implemented.", NamedTextColor.RED));
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.supplyAsync(() -> filterByPermission(invocation, Arrays.asList("reload", "info"), "queue."));
    }
}

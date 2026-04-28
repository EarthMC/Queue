package net.earthmc.queue.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import net.earthmc.queue.QueuePlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class Brig {
    public static CompletableFuture<Suggestions> filterByStart(CommandContext<CommandSource> context, SuggestionsBuilder builder, Iterable<String> suggestions) {
        final String argument = builder.getRemaining();

        for (String suggestion : suggestions) {
            if (suggestion.regionMatches(true, 0, argument, 0, argument.length())) {
                builder.suggest(suggestion);
            }
        }

        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> filterByPermission(final CommandContext<CommandSource> context, final SuggestionsBuilder builder, Collection<String> suggestions, String permPrefix) {
        List<String> strings = new ArrayList<>(suggestions);
        strings.removeIf(string -> !hasPrefixedPermission(context.getSource(), permPrefix, string));

        return filterByStart(context, builder, strings);
    }

    public static boolean hasPrefixedPermission(@NotNull PermissionSubject subject, @NotNull String permPrefix, @Nullable String arg) {
        if (arg != null && subject.getPermissionValue(permPrefix + arg.toLowerCase(Locale.ROOT)) == Tristate.FALSE)
            return false;

        return subject.hasPermission(permPrefix + "*") || (arg != null && subject.hasPermission(permPrefix + arg.toLowerCase(Locale.ROOT)));
    }

    public static CompletableFuture<Suggestions> suggestOnlinePlayers(final CommandContext<CommandSource> context, final SuggestionsBuilder builder) {
        final List<String> onlinePlayers = QueuePlugin.instance().proxy().getAllPlayers().stream().map(Player::getUsername).toList();
        return filterByStart(context, builder, onlinePlayers);
    }
}

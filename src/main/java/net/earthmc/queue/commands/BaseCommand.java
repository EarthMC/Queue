package net.earthmc.queue.commands;

import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class BaseCommand {
    public List<String> filterByStart(Collection<String> collection, String startingWith) {
        return collection.stream().filter(s -> s.regionMatches(true, 0, startingWith, 0, startingWith.length())).collect(Collectors.toList());
    }

    public List<String> filterByPermission(@NotNull PermissionSubject subject, Collection<String> collection, String permPrefix, @Nullable String startingWith) {
        List<String> strings = new ArrayList<>(collection);
        strings.removeIf(string -> !hasPrefixedPermission(subject, permPrefix, string));
        return startingWith != null ? filterByStart(strings, startingWith) : strings;
    }

    public static boolean hasPrefixedPermission(@NotNull PermissionSubject subject, @NotNull String permPrefix, @Nullable String arg) {
        if (arg != null && subject.getPermissionValue(permPrefix + arg.toLowerCase(Locale.ROOT)) == Tristate.FALSE)
            return false;

        return subject.hasPermission(permPrefix + "*") || (arg != null && subject.hasPermission(permPrefix + arg.toLowerCase(Locale.ROOT)));
    }
}

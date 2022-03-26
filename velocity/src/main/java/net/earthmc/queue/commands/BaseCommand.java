package net.earthmc.queue.commands;

import com.velocitypowered.api.command.SimpleCommand.Invocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BaseCommand {
    public List<String> filterByStart(Collection<String> collection, String arg) {
        return collection.stream().filter(s -> s.toLowerCase().startsWith(arg)).collect(Collectors.toList());
    }

    public List<String> filterByPermission(Invocation invocation, Collection<String> collection, String permPrefix) {
        List<String> strings = new ArrayList<>(collection);
        strings.removeIf(string -> !invocation.source().hasPermission(permPrefix + string) && !invocation.source().hasPermission(permPrefix + "*"));
        return invocation.arguments().length > 0 ? filterByStart(strings, invocation.arguments()[0]) : strings;
    }
}

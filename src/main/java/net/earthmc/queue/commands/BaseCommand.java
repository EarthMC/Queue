package net.earthmc.queue.commands;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BaseCommand {
    public List<String> filterByStart(Collection<String> collection, String arg) {
        return collection.stream().filter(s -> s.toLowerCase().startsWith(arg)).collect(Collectors.toList());
    }
}

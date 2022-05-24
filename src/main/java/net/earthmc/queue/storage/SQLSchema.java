package net.earthmc.queue.storage;

import java.util.HashSet;
import java.util.Set;

public class SQLSchema {
    public static Set<String> getPlayerColumns() {
        Set<String> columns = new HashSet<>();
        columns.add("`lastJoinedServer` mediumtext default null");
        columns.add("`autoQueueDisabled` bool not null default 0");
        return columns;
    }
}

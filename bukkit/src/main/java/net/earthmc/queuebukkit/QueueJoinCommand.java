package net.earthmc.queuebukkit;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public class QueueJoinCommand implements CommandExecutor {

    private final QueueBukkit plugin;

    public QueueJoinCommand(QueueBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof ConsoleCommandSender))
            return true;

        if (args.length >= 2) {
            Player player = Bukkit.getPlayer(args[0]);
            if (player == null)
                return true;

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(player.getName());
            out.writeUTF(args[1]);

            player.sendPluginMessage(plugin, "queue:join", out.toByteArray());
        }

        return true;
    }
}

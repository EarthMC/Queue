package net.earthmc.queuebukkit;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public final class QueueBukkit extends JavaPlugin implements PluginMessageListener {

    private static final Sound alertSound = Sound.sound(Key.key(Key.MINECRAFT_NAMESPACE, "entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, 1.0f);

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, "queue:sound", this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "queue:join");
        Bukkit.getPluginCommand("queuejoin").setExecutor(new QueueJoinCommand(this));
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        Player pl = getServer().getPlayer(new String(message, StandardCharsets.UTF_8));
        if (pl != null)
            pl.playSound(alertSound);
    }
}

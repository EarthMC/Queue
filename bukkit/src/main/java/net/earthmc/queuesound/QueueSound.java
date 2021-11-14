package net.earthmc.queuesound;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class QueueSound extends JavaPlugin implements PluginMessageListener {

    private static final Sound alertSound = Sound.sound(Key.key(Key.MINECRAFT_NAMESPACE, "entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, 1.0f);

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, "queue:sound", this);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("queue:sound"))
            return;

        ByteArrayDataInput input = ByteStreams.newDataInput(message);

        Player pl = getServer().getPlayer(input.readUTF());
        if (pl != null)
            pl.playSound(alertSound);
    }
}

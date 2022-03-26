package net.earthmc.queue;

import net.earthmc.queue.object.Weighted;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class Priority extends Weighted {
    public final String name;
    public final Component message;

    public Priority(String name, int weight, Component message) {
        super(weight);
        this.name = name;
        this.message = message;
    }

    public String name() {
        return this.name;
    }

    public Component message() {
        return this.message;
    }

    @Override
    public String toString() {
        return "Priority{" +
                "name='" + name + '\'' +
                ", weight=" + weight +
                ", message=" + MiniMessage.miniMessage().serialize(message) +
                '}';
    }
}

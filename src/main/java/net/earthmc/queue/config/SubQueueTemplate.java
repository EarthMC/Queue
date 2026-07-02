package net.earthmc.queue.config;

public record SubQueueTemplate(String name, int weight, int maxSends) {
}

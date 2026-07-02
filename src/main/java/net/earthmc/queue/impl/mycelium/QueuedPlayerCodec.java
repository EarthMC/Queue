package net.earthmc.queue.impl.mycelium;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import net.earthmc.mycelium.api.serialization.JsonCodec;
import net.earthmc.queue.QueuedPlayer;

import java.lang.reflect.Type;
import java.util.UUID;

public class QueuedPlayerCodec implements JsonCodec<QueuedPlayer> {
    public static final QueuedPlayerCodec INSTANCE = new QueuedPlayerCodec();

    @Override
    public Type type() {
        return QueuedPlayer.class;
    }

    @Override
    public QueuedPlayer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        final JsonObject object = json.getAsJsonObject();

        final UUID uuid = UUID.fromString(object.get("uuid").getAsString());
        return new RemoteQueuedPlayer(uuid, object.get("name").getAsString());
    }

    @Override
    public JsonElement serialize(QueuedPlayer src, Type typeOfSrc, JsonSerializationContext context) {
        final JsonObject object = new JsonObject();
        object.addProperty("name", src.name());
        object.addProperty("uuid", src.uuid().toString());
        return object;
    }
}

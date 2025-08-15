package fr.skyblock.utils;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Type;

/**
 * Adaptateur Gson pour sérialiser/désérialiser les objets Location de Bukkit
 * Résout le problème d'accès aux champs java.lang.ref.Reference
 */
public class LocationAdapter implements JsonSerializer<Location>, JsonDeserializer<Location> {

    @Override
    public JsonElement serialize(Location src, Type typeOfSrc, JsonSerializationContext context) {
        if (src == null) {
            return JsonNull.INSTANCE;
        }

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("world", src.getWorld().getName());
        jsonObject.addProperty("x", src.getX());
        jsonObject.addProperty("y", src.getY());
        jsonObject.addProperty("z", src.getZ());
        jsonObject.addProperty("yaw", src.getYaw());
        jsonObject.addProperty("pitch", src.getPitch());
        return jsonObject;
    }

    @Override
    public Location deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (json.isJsonNull()) {
            return null;
        }

        JsonObject jsonObject = json.getAsJsonObject();
        String worldName = jsonObject.get("world").getAsString();
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            throw new JsonParseException("World '" + worldName + "' not found during Location deserialization");
        }

        double x = jsonObject.get("x").getAsDouble();
        double y = jsonObject.get("y").getAsDouble();
        double z = jsonObject.get("z").getAsDouble();
        float yaw = jsonObject.has("yaw") ? jsonObject.get("yaw").getAsFloat() : 0.0f;
        float pitch = jsonObject.has("pitch") ? jsonObject.get("pitch").getAsFloat() : 0.0f;

        return new Location(world, x, y, z, yaw, pitch);
    }
}
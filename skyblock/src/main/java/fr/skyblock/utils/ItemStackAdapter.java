package fr.skyblock.utils;

import com.google.gson.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.util.Base64;

/**
 * Adaptateur Gson pour sérialiser/désérialiser les objets ItemStack de Bukkit
 * Utilise la sérialisation Bukkit native pour éviter les problèmes de réflection
 */
public class ItemStackAdapter implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {

    @Override
    public JsonElement serialize(ItemStack src, Type typeOfSrc, JsonSerializationContext context) {
        if (src == null || src.getType() == Material.AIR) {
            return JsonNull.INSTANCE;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(src);
            dataOutput.close();

            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return new JsonPrimitive(base64);

        } catch (Exception e) {
            throw new JsonIOException("Failed to serialize ItemStack", e);
        }
    }

    @Override
    public ItemStack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (json.isJsonNull()) {
            return null;
        }

        try {
            String base64 = json.getAsString();
            byte[] data = Base64.getDecoder().decode(base64);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack itemStack = (ItemStack) dataInput.readObject();
            dataInput.close();

            return itemStack;

        } catch (Exception e) {
            throw new JsonParseException("Failed to deserialize ItemStack", e);
        }
    }
}
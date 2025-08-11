package fr.prisontycoon.utils;

import com.fastasyncworldedit.core.configuration.Config;
import com.google.gson.*;
import org.bukkit.DyeColor;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;

import java.lang.reflect.Type;

public class PatternAdapter implements JsonSerializer<Pattern>, JsonDeserializer<Pattern> {

    @Override
    public JsonElement serialize(Pattern src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("color", src.getColor().name());
        jsonObject.addProperty("pattern", src.getPattern().name());
        return jsonObject;
    }

    @Override
    public Pattern deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        DyeColor color = DyeColor.valueOf(jsonObject.get("color").getAsString());
        PatternType patternType = PatternType.valueOf(jsonObject.get("pattern").getAsString());
        return new Pattern(color, patternType);
    }
}
package com.talhanation.bannermod.entity.military.perks;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PerkReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    public PerkReloadListener() {
        super(GSON, "perks");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<PerkNode> nodes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            PerkNode node = parse(entry.getKey(), entry.getValue());
            if (!ids.add(node.id())) {
                throw new IllegalStateException("Duplicate perk id in datapack JSON: " + node.id());
            }
            nodes.add(node);
        }

        PerkRegistry.replaceAll(nodes);
        BannerModMain.LOGGER.info("Loaded {} perks from datapacks", nodes.size());
    }

    private static PerkNode parse(ResourceLocation source, JsonElement json) {
        if (!json.isJsonObject()) {
            throw new JsonParseException("Perk " + source + " must be a JSON object");
        }
        JsonObject object = json.getAsJsonObject();
        String id = requiredString(object, "id", source);
        PerkArchetype archetype = enumValue(PerkArchetype.class, requiredString(object, "archetype", source), "archetype", source);
        int pointCost = requiredInt(object, "point_cost", source);
        List<String> prerequisites = strings(object, "prerequisites", source);
        List<PerkBonus> bonuses = bonuses(object, source);
        return new PerkNode(id, archetype, pointCost, prerequisites, bonuses);
    }

    private static List<PerkBonus> bonuses(JsonObject object, ResourceLocation source) {
        JsonArray array = requiredArray(object, "bonuses", source);
        List<PerkBonus> bonuses = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("Perk " + source + " bonus entries must be objects");
            }
            JsonObject bonus = element.getAsJsonObject();
            PerkStat stat = enumValue(PerkStat.class, requiredString(bonus, "stat", source), "stat", source);
            double amount = requiredDouble(bonus, "amount", source);
            bonuses.add(new PerkBonus(stat, amount));
        }
        return bonuses;
    }

    private static List<String> strings(JsonObject object, String key, ResourceLocation source) {
        JsonElement element = object.get(key);
        if (element == null) return List.of();
        if (!element.isJsonArray()) {
            throw new JsonParseException("Perk " + source + " field '" + key + "' must be an array");
        }
        List<String> values = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Perk " + source + " field '" + key + "' must contain only strings");
            }
            values.add(value.getAsString());
        }
        return values;
    }

    private static String requiredString(JsonObject object, String key, ResourceLocation source) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Perk " + source + " requires string field '" + key + "'");
        }
        return element.getAsString();
    }

    private static int requiredInt(JsonObject object, String key, ResourceLocation source) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("Perk " + source + " requires numeric field '" + key + "'");
        }
        return element.getAsInt();
    }

    private static double requiredDouble(JsonObject object, String key, ResourceLocation source) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("Perk " + source + " requires numeric field '" + key + "'");
        }
        return element.getAsDouble();
    }

    private static JsonArray requiredArray(JsonObject object, String key, ResourceLocation source) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException("Perk " + source + " requires array field '" + key + "'");
        }
        return element.getAsJsonArray();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String key, ResourceLocation source) {
        try {
            return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new JsonParseException("Perk " + source + " has invalid '" + key + "': " + value, ex);
        }
    }
}

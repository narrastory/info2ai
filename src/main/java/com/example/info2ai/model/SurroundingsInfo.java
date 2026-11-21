package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record SurroundingsInfo(
    String biome,
    String biomeAtFeet,
    Facts light,
    Boolean canSeeSky,
    Double temperature,
    Double humidity,
    Double enclosedRatio,
    Integer caveDepth,
    List<Facts> structuresNearby,
    List<EntitySummary> entities,
    Facts entityCounts,
    List<Facts> blockEntities,
    List<Facts> drops,
    List<Facts> sounds,
    Facts hud,
    Facts performance
) implements JsonModel {
    public SurroundingsInfo {
        structuresNearby = JsonUtil.immutableSlots(structuresNearby);
        entities = JsonUtil.immutableSlots(entities);
        blockEntities = JsonUtil.immutableSlots(blockEntities);
        drops = JsonUtil.immutableSlots(drops);
        sounds = JsonUtil.immutableSlots(sounds);
    }

    public static SurroundingsInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new SurroundingsInfo(
            JsonUtil.string(o, "biome"),
            JsonUtil.string(o, "biome_at_feet"),
            Facts.fromJson(JsonUtil.object(o, "light")),
            JsonUtil.bool(o, "can_see_sky"),
            JsonUtil.number(o, "temperature"),
            JsonUtil.number(o, "humidity"),
            JsonUtil.number(o, "enclosed_ratio"),
            JsonUtil.integer(o, "cave_depth"),
            JsonUtil.list(o, "structures_nearby", e -> Facts.fromJson(e.getAsJsonObject())),
            JsonUtil.list(o, "entities", e -> EntitySummary.fromJson(e.getAsJsonObject())),
            Facts.fromJson(JsonUtil.object(o, "entity_counts")),
            JsonUtil.list(o, "block_entities", e -> Facts.fromJson(e.getAsJsonObject())),
            JsonUtil.list(o, "drops", e -> Facts.fromJson(e.getAsJsonObject())),
            JsonUtil.list(o, "sounds", e -> Facts.fromJson(e.getAsJsonObject())),
            Facts.fromJson(JsonUtil.object(o, "hud")),
            Facts.fromJson(JsonUtil.object(o, "performance"))
        );
    }

    private List<Facts> projectContainers(Detail detail) {
        if (blockEntities == null || detail == Detail.FULL) return blockEntities;
        return blockEntities.stream().map(f -> {
            JsonObject value = f.toJson();
            var source = value.get("items");
            if (source != null && source.isJsonArray()) {
                java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
                source.getAsJsonArray().forEach(item -> {
                    if (item.isJsonObject()) ids.add(item.getAsJsonObject().get("id").getAsString());
                    else if (item.isJsonPrimitive()) ids.add(item.getAsString());
                });
                com.google.gson.JsonArray summary = new com.google.gson.JsonArray();
                ids.stream().limit(12).forEach(summary::add);
                value.add("items", summary);
                // Preserve existing fixture fields unless truncation is necessary.
                if (ids.size() > 12 || value.has("items_truncated")) value.addProperty("items_truncated", ids.size() > 12);
            }
            return Facts.fromJson(value);
        }).toList();
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "biome", biome, detail);
        JsonUtil.put(o, "biome_at_feet", biomeAtFeet, detail);
        JsonUtil.put(o, "light", light, detail);
        JsonUtil.put(o, "can_see_sky", canSeeSky, detail);
        JsonUtil.put(o, "temperature", temperature == null ? null : JsonUtil.round3(temperature), detail);
        JsonUtil.put(o, "humidity", humidity == null ? null : JsonUtil.round3(humidity), detail);
        JsonUtil.put(o, "enclosed_ratio", enclosedRatio == null ? null : JsonUtil.round3(enclosedRatio), detail);
        JsonUtil.put(o, "cave_depth", caveDepth, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "structures_nearby", structuresNearby, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "entities", entities, detail);
        JsonUtil.put(o, "entity_counts", entityCounts, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "block_entities", projectContainers(detail), detail);
        if (detail == Detail.FULL) JsonUtil.put(o, "drops", drops, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "sounds", sounds, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "hud", hud, detail);
        if (detail == Detail.FULL) JsonUtil.put(o, "performance", performance, detail);
        return o;
    }
}

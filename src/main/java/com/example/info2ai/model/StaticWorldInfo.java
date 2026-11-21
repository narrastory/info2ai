package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record StaticWorldInfo(
    String source,
    String confidence,
    Facts biome,
    String biomeScaleNote,
    List<StructureInfo> structures,
    List<Facts> nearestBiomes,
    Facts query,
    Facts coverage,
    Long sampledAtMs
) implements JsonModel {
    public StaticWorldInfo(String source,String confidence,Facts biome,String biomeScaleNote,List<StructureInfo> structures,List<Facts> nearestBiomes) {
        this(source,confidence,biome,biomeScaleNote,structures,nearestBiomes,null,null,null);
    }
    public StaticWorldInfo {
        structures = JsonUtil.immutableSlots(structures);
        nearestBiomes = JsonUtil.immutableSlots(nearestBiomes);
    }

    public static StaticWorldInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new StaticWorldInfo(
            JsonUtil.string(o, "source"),
            JsonUtil.string(o, "confidence"),
            Facts.fromJson(JsonUtil.object(o, "biome")),
            JsonUtil.string(o, "biome_scale_note"),
            JsonUtil.list(o, "structures", e -> StructureInfo.fromJson(e.getAsJsonObject())),
            JsonUtil.list(o, "nearest_biomes", e -> Facts.fromJson(e.getAsJsonObject())),
            Facts.fromJson(JsonUtil.object(o,"query")),Facts.fromJson(JsonUtil.object(o,"coverage")),JsonUtil.longNumber(o,"sampled_at_ms")
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "source", source, detail);
        JsonUtil.put(o, "confidence", confidence, detail);
        JsonUtil.put(o, "biome", biome, detail);
        JsonUtil.put(o, "biome_scale_note", biomeScaleNote, detail);
        JsonUtil.put(o, "structures", structures, detail);
        if (detail == Detail.FULL) JsonUtil.put(o, "nearest_biomes", nearestBiomes, detail);
        if(query!=null)JsonUtil.put(o,"query",query,detail);
        if(coverage!=null)JsonUtil.put(o,"coverage",coverage,detail);
        if(sampledAtMs!=null)JsonUtil.put(o,"sampled_at_ms",sampledAtMs,detail);
        return o;
    }
}

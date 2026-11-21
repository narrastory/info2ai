package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record WorldInfo(
    String dimension,
    String dimensionId,
    TimeInfo time,
    WeatherInfo weather,
    Facts stats,
    StaticWorldInfo staticWorld
) implements JsonModel {
    public static WorldInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new WorldInfo(
            JsonUtil.string(o, "dimension"),
            JsonUtil.string(o, "dimension_id"),
            TimeInfo.fromJson(JsonUtil.object(o, "time")),
            WeatherInfo.fromJson(JsonUtil.object(o, "weather")),
            Facts.fromJson(JsonUtil.object(o, "stats")),
            StaticWorldInfo.fromJson(JsonUtil.object(o, "static"))
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "dimension", dimension, detail);
        JsonUtil.put(o, "dimension_id", dimensionId, detail);
        JsonUtil.put(o, "time", time, detail);
        JsonUtil.put(o, "weather", weather, detail);
        JsonUtil.put(o, "stats", stats, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "static", staticWorld, detail);
        return o;
    }
}

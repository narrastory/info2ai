package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record WeatherInfo(
    Boolean rain,
    Boolean thunder,
    Double rainLevel,
    Double thunderLevel,
    Integer secondsSinceWeatherChange
) implements JsonModel {
    public static WeatherInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new WeatherInfo(
            JsonUtil.bool(o, "rain"),
            JsonUtil.bool(o, "thunder"),
            JsonUtil.number(o, "rain_level"),
            JsonUtil.number(o, "thunder_level"),
            JsonUtil.integer(o, "seconds_since_weather_change")
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "rain", rain, detail);
        JsonUtil.put(o, "thunder", thunder, detail);
        JsonUtil.put(o, "rain_level", rainLevel == null ? null : JsonUtil.round3(rainLevel), detail);
        JsonUtil.put(o, "thunder_level", thunderLevel == null ? null : JsonUtil.round3(thunderLevel), detail);
        JsonUtil.put(o, "seconds_since_weather_change", secondsSinceWeatherChange, detail);
        return o;
    }
}

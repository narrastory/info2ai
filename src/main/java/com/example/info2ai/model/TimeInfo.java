package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record TimeInfo(
    Integer day,
    Integer dayTime,
    String phase,
    String clock,
    Integer moonPhase,
    Boolean isDay
) implements JsonModel {
    public static TimeInfo fromTicks(long dayTime) {
        int daytime = (int) Math.floorMod(dayTime, 24000L);
        int minute = (int) ((daytime + 6000L) % 24000 * 60 / 1000);
        String clock = String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
        String phase = daytime >= 23000 ? "dawn" : daytime >= 18000 ? "midnight" : daytime >= 13000 ? "night" : daytime >= 12000 ? "dusk" : daytime >= 6000 ? "afternoon" : daytime >= 5000 ? "noon" : "morning";
        return new TimeInfo(Math.toIntExact(Math.floorDiv(dayTime, 24000) + 1), daytime, phase, clock, (int) Math.floorMod(Math.floorDiv(dayTime,24000),8), daytime < 12000);
    }

    public static TimeInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new TimeInfo(
            JsonUtil.integer(o, "day"),
            JsonUtil.integer(o, "day_time"),
            JsonUtil.string(o, "phase"),
            JsonUtil.string(o, "clock"),
            JsonUtil.integer(o, "moon_phase"),
            JsonUtil.bool(o, "is_day")
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "day", day, detail);
        JsonUtil.put(o, "day_time", dayTime, detail);
        JsonUtil.put(o, "phase", phase, detail);
        JsonUtil.put(o, "clock", clock, detail);
        JsonUtil.put(o, "moon_phase", moonPhase, detail);
        JsonUtil.put(o, "is_day", isDay, detail);
        return o;
    }
}

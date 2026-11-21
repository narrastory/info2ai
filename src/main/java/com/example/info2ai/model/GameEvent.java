package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record GameEvent(
    Long seq,
    Long t,
    Long tick,
    String kind,
    String severity,
    Long durationMs,
    String text,
    Facts data,
    List<Double> pos
) implements JsonModel {
    public GameEvent {
        pos = JsonUtil.immutableSlots(pos);
    }

    public static GameEvent fromJson(JsonObject o) {
        if (o == null) return null;
        return new GameEvent(
            JsonUtil.longNumber(o, "seq"),
            JsonUtil.longNumber(o, "t"),
            JsonUtil.longNumber(o, "tick"),
            JsonUtil.string(o, "kind"),
            JsonUtil.string(o, "severity"),
            JsonUtil.longNumber(o, "duration_ms"),
            JsonUtil.string(o, "text"),
            Facts.fromJson(JsonUtil.object(o, "data")),
            JsonUtil.doubles(o, "pos")
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "seq", seq, detail);
        JsonUtil.put(o, "t", t, detail);
        JsonUtil.put(o, "tick", tick, detail);
        JsonUtil.put(o, "kind", kind, detail);
        JsonUtil.put(o, "severity", severity, detail);
        JsonUtil.put(o, "duration_ms", durationMs, detail);
        JsonUtil.put(o, "text", text, detail);
        JsonUtil.put(o, "data", data, detail);
        JsonUtil.put(o, "pos", pos == null ? null : pos.stream().map(JsonUtil::round2).toList(), detail);
        return o;
    }
}

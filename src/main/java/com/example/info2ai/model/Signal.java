package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record Signal(
    String kind,
    String severity,
    String note,
    Integer sinceS,
    Facts detail
) implements JsonModel {
    public static Signal fromJson(JsonObject o) {
        if (o == null) return null;
        return new Signal(
            JsonUtil.string(o, "kind"),
            JsonUtil.string(o, "severity"),
            JsonUtil.string(o, "note"),
            JsonUtil.integer(o, "since_s"),
            Facts.fromJson(JsonUtil.object(o, "detail"))
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "kind", kind, detail);
        JsonUtil.put(o, "severity", severity, detail);
        JsonUtil.put(o, "note", note, detail);
        JsonUtil.put(o, "since_s", sinceS, detail);
        JsonUtil.put(o, "detail", this.detail, detail);
        return o;
    }
}

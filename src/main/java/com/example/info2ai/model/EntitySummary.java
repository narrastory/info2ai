package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record EntitySummary(
    String type,
    String customName,
    Integer idLocal,
    List<Double> pos,
    Double distance,
    String direction,
    Double health,
    Boolean hostile,
    Boolean targetingPlayer,
    Boolean glowing,
    Boolean baby,
    Boolean tamed,
    ItemStackInfo held
) implements JsonModel {
    public EntitySummary {
        pos = JsonUtil.immutableSlots(pos);
    }

    public static EntitySummary fromJson(JsonObject o) {
        if (o == null) return null;
        return new EntitySummary(
            JsonUtil.string(o, "type"),
            JsonUtil.string(o, "custom_name"),
            JsonUtil.integer(o, "id_local"),
            JsonUtil.doubles(o, "pos"),
            JsonUtil.number(o, "distance"),
            JsonUtil.string(o, "direction"),
            JsonUtil.number(o, "health"),
            JsonUtil.bool(o, "hostile"),
            JsonUtil.bool(o, "targeting_player"),
            JsonUtil.bool(o, "glowing"),
            JsonUtil.bool(o, "baby"),
            JsonUtil.bool(o, "tamed"),
            ItemStackInfo.fromJson(JsonUtil.object(o, "held"))
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "type", type, detail);
        JsonUtil.put(o, "custom_name", customName, detail);
        JsonUtil.put(o, "id_local", idLocal, detail);
        JsonUtil.put(o, "pos", pos == null ? null : pos.stream().map(JsonUtil::round2).toList(), detail);
        JsonUtil.put(o, "distance", distance == null ? null : JsonUtil.round1(distance), detail);
        JsonUtil.put(o, "direction", direction, detail);
        JsonUtil.put(o, "health", health == null ? null : JsonUtil.round3(health), detail);
        JsonUtil.put(o, "hostile", hostile, detail);
        JsonUtil.put(o, "targeting_player", targetingPlayer, detail);
        JsonUtil.put(o, "glowing", glowing, detail);
        JsonUtil.put(o, "baby", baby, detail);
        JsonUtil.put(o, "tamed", tamed, detail);
        JsonUtil.put(o, "held", held, detail);
        return o;
    }
}

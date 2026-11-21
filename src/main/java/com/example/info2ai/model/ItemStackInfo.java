package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record ItemStackInfo(
    String id,
    Integer count,
    Integer damage,
    Integer damageMax,
    String name,
    List<Facts> enchantments,
    String nbtSummary,
    Integer slot,
    Long directorAction,
    boolean directorActionKnown
) implements JsonModel {
    public ItemStackInfo(String id, Integer count, Integer damage, Integer damageMax, String name,
                         List<Facts> enchantments, String nbtSummary, Integer slot) {
        this(id, count, damage, damageMax, name, enchantments, nbtSummary, slot, null, false);
    }
    public ItemStackInfo(String id, Integer count, Integer damage, Integer damageMax, String name,
                         List<Facts> enchantments, String nbtSummary, Integer slot, Long directorAction) {
        this(id, count, damage, damageMax, name, enchantments, nbtSummary, slot, directorAction, true);
    }
    public ItemStackInfo {
        enchantments = JsonUtil.immutableSlots(enchantments);
    }

    public static ItemStackInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new ItemStackInfo(
            JsonUtil.string(o, "id"),
            JsonUtil.integer(o, "count"),
            JsonUtil.integer(o, "damage"),
            JsonUtil.integer(o, "damage_max"),
            JsonUtil.string(o, "name"),
            JsonUtil.list(o, "enchantments", e -> Facts.fromJson(e.getAsJsonObject())),
            JsonUtil.string(o, "nbt_summary"),
            JsonUtil.integer(o, "slot"),
            JsonUtil.longNumber(o, "director_action"),
            o.has("director_action")
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "id", id, detail);
        JsonUtil.put(o, "count", count, detail);
        JsonUtil.put(o, "damage", damage, detail);
        JsonUtil.put(o, "damage_max", damageMax, detail);
        JsonUtil.put(o, "name", name, detail);
        JsonUtil.put(o, "enchantments", enchantments, detail);
        if (detail == Detail.FULL) JsonUtil.put(o, "nbt_summary", nbtSummary, detail);
        JsonUtil.put(o, "slot", slot, detail);
        if (detail != Detail.LITE && directorActionKnown) JsonUtil.putNullable(o, "director_action", directorAction);
        return o;
    }
}

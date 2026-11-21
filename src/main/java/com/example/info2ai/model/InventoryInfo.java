package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record InventoryInfo(
    Integer used,
    Integer free,
    List<ItemStackInfo> main,
    List<ItemStackInfo> hotbar,
    List<ItemStackInfo> armor,
    ItemStackInfo offhand,
    Facts enderChest,
    ItemStackInfo cursor,
    Facts aggregate,
    String summaryZh
) implements JsonModel {
    public InventoryInfo {
        main = JsonUtil.immutableSlots(main);
        hotbar = JsonUtil.immutableSlots(hotbar);
        armor = JsonUtil.immutableSlots(armor);
    }

    public static InventoryInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new InventoryInfo(
            JsonUtil.integer(o, "used"),
            JsonUtil.integer(o, "free"),
            JsonUtil.list(o, "main", e -> ItemStackInfo.fromJson(e.getAsJsonObject())),
            JsonUtil.list(o, "hotbar", e -> ItemStackInfo.fromJson(e.getAsJsonObject())),
            JsonUtil.list(o, "armor", e -> ItemStackInfo.fromJson(e.getAsJsonObject())),
            ItemStackInfo.fromJson(JsonUtil.object(o, "offhand")),
            Facts.fromJson(JsonUtil.object(o, "ender_chest")),
            ItemStackInfo.fromJson(JsonUtil.object(o, "cursor")),
            Facts.fromJson(JsonUtil.object(o, "aggregate")),
            JsonUtil.string(o, "summary_zh")
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "used", used, detail);
        JsonUtil.put(o, "free", free, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "main", main, detail);
        JsonUtil.put(o, "hotbar", hotbar, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "armor", armor, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "offhand", offhand, detail);
        if (detail == Detail.FULL) JsonUtil.put(o, "ender_chest", enderChest, detail);
        if (detail == Detail.FULL) JsonUtil.put(o, "cursor", cursor, detail);
        JsonUtil.put(o, "aggregate", aggregate, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "summary_zh", summaryZh, detail);
        return o;
    }
}

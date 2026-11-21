package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record ProgressInfo(
    Integer advancementsDone,
    List<Facts> recentAdvancements,
    Facts stats,
    Integer recipesUnlocked,
    Boolean dragonKilled,
    Boolean endVisited,
    Boolean netherVisited,
    List<Facts> mapMarkers
) implements JsonModel {
    public ProgressInfo {
        recentAdvancements = JsonUtil.immutableSlots(recentAdvancements);
        mapMarkers = JsonUtil.immutableSlots(mapMarkers);
    }

    public static ProgressInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new ProgressInfo(
            JsonUtil.integer(o, "advancements_done"),
            JsonUtil.list(o, "recent_advancements", e -> Facts.fromJson(e.getAsJsonObject())),
            Facts.fromJson(JsonUtil.object(o, "stats")),
            JsonUtil.integer(o, "recipes_unlocked"),
            JsonUtil.bool(o, "dragon_killed"),
            JsonUtil.bool(o, "end_visited"),
            JsonUtil.bool(o, "nether_visited"),
            JsonUtil.list(o, "map_markers", e -> Facts.fromJson(e.getAsJsonObject()))
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "advancements_done", advancementsDone, detail);
        JsonUtil.put(o, "recent_advancements", recentAdvancements, detail);
        JsonUtil.put(o, "stats", stats, detail);
        JsonUtil.put(o, "recipes_unlocked", recipesUnlocked, detail);
        JsonUtil.put(o, "dragon_killed", dragonKilled, detail);
        JsonUtil.put(o, "end_visited", endVisited, detail);
        JsonUtil.put(o, "nether_visited", netherVisited, detail);
        if (detail == Detail.FULL) JsonUtil.put(o, "map_markers", mapMarkers, detail);
        return o;
    }
}

package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record HistoryInfo(
    Integer sessionEvents,
    Integer deathsThisSession,
    Facts lastDeath,
    List<Facts> dimensionSwitches,
    List<List<Double>> recentPositions,
    List<Facts> visitedStructures,
    Integer longestSilenceS,
    Boolean afk,
    Facts previousSession,
    Facts memory
) implements JsonModel {
    public HistoryInfo(Integer sessionEvents,Integer deathsThisSession,Facts lastDeath,List<Facts> dimensionSwitches,
        List<List<Double>> recentPositions,List<Facts> visitedStructures,Integer longestSilenceS,Boolean afk,Facts previousSession) {
        this(sessionEvents,deathsThisSession,lastDeath,dimensionSwitches,recentPositions,visitedStructures,longestSilenceS,afk,previousSession,null);
    }
    public HistoryInfo {
        dimensionSwitches = JsonUtil.immutableSlots(dimensionSwitches);
        recentPositions = JsonUtil.immutablePositions(recentPositions);
        visitedStructures = JsonUtil.immutableSlots(visitedStructures);
    }

    public static HistoryInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new HistoryInfo(
            JsonUtil.integer(o, "session_events"),
            JsonUtil.integer(o, "deaths_this_session"),
            Facts.fromJson(JsonUtil.object(o, "last_death")),
            JsonUtil.list(o, "dimension_switches", e -> Facts.fromJson(e.getAsJsonObject())),
            JsonUtil.positions(o, "recent_positions"),
            JsonUtil.list(o, "visited_structures", e -> Facts.fromJson(e.getAsJsonObject())),
            JsonUtil.integer(o, "longest_silence_s"),
            JsonUtil.bool(o, "afk"),
            Facts.fromJson(JsonUtil.object(o, "previous_session")),
            Facts.fromJson(JsonUtil.object(o, "memory"))
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "session_events", sessionEvents, detail);
        JsonUtil.put(o, "deaths_this_session", deathsThisSession, detail);
        JsonUtil.put(o, "last_death", lastDeath, detail);
        JsonUtil.put(o, "dimension_switches", dimensionSwitches, detail);
        JsonUtil.put(o, "recent_positions", recentPositions, detail);
        JsonUtil.put(o, "visited_structures", visitedStructures, detail);
        JsonUtil.put(o, "longest_silence_s", longestSilenceS, detail);
        JsonUtil.put(o, "afk", afk, detail);
        JsonUtil.put(o, "previous_session", previousSession, detail);
        if(memory!=null)JsonUtil.put(o,"memory",memory,detail);
        return o;
    }
}

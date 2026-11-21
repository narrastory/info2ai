package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record SessionInfo(
    String worldName,
    String worldId,
    String seed,
    String gameMode,
    String difficulty,
    Boolean hardcore,
    String mcVersion,
    String loader,
    Integer modCount,
    List<String> mods,
    List<String> modIds,
    Integer uptimeS,
    Integer totalPlaytimeS,
    Boolean paused
) implements JsonModel {
    public SessionInfo {
        mods = JsonUtil.immutableSlots(mods);
        modIds = JsonUtil.immutableSlots(modIds);
    }

    public static SessionInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new SessionInfo(
            JsonUtil.string(o, "world_name"),
            JsonUtil.string(o, "world_id"),
            JsonUtil.string(o, "seed"),
            JsonUtil.string(o, "game_mode"),
            JsonUtil.string(o, "difficulty"),
            JsonUtil.bool(o, "hardcore"),
            JsonUtil.string(o, "mc_version"),
            JsonUtil.string(o, "loader"),
            JsonUtil.integer(o, "mod_count"),
            JsonUtil.strings(o, "mods"),
            JsonUtil.strings(o, "mod_ids"),
            JsonUtil.integer(o, "uptime_s"),
            JsonUtil.integer(o, "total_playtime_s"),
            JsonUtil.bool(o, "paused")
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "world_name", worldName, detail);
        JsonUtil.put(o, "world_id", worldId, detail);
        JsonUtil.put(o, "seed", seed, detail);
        JsonUtil.put(o, "game_mode", gameMode, detail);
        JsonUtil.put(o, "difficulty", difficulty, detail);
        JsonUtil.put(o, "hardcore", hardcore, detail);
        JsonUtil.put(o, "mc_version", mcVersion, detail);
        JsonUtil.put(o, "loader", loader, detail);
        JsonUtil.put(o, "mod_count", modCount, detail);
        if (detail == Detail.FULL) JsonUtil.put(o, "mods", mods, detail);
        // 历史实测 Lite 4088B（session 1671B / mod_ids 1372B，55 mods）。
        // 现由 Snapshot 的 compact_v1 投影限制 Lite；normal/full 仍保留这里的完整会话事实，实际游戏 wire 待复测。
        if (detail != Detail.FULL) JsonUtil.put(o, "mod_ids", modIds, detail);
        JsonUtil.put(o, "uptime_s", uptimeS, detail);
        JsonUtil.put(o, "total_playtime_s", totalPlaytimeS, detail);
        JsonUtil.put(o, "paused", paused, detail);
        return o;
    }
}

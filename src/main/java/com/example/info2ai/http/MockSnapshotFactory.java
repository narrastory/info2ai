package com.example.info2ai.http;

import com.example.info2ai.model.Snapshot;
import com.google.gson.*;

/** 独立于游戏的模拟数据，名称明确标为 MOCK。 */
public final class MockSnapshotFactory {
    private MockSnapshotFactory() { }
    public static Snapshot create() {
        JsonObject data=JsonParser.parseString("""
            {"v":1,"seq":0,"captured_at":0,"tick":264000,"detail":"full",
             "session":{"world_name":"MOCK 草甸世界","world_id":"info2ai-mock","seed":"0","game_mode":"survival","difficulty":"normal","hardcore":false,"mc_version":"1.21.1","loader":"fabric","mod_count":3,"mod_ids":["minecraft","fabric-api","info2ai"],"uptime_s":0,"total_playtime_s":0,"paused":false},
             "world":{"dimension":"minecraft:overworld","dimension_id":"minecraft:overworld","time":{"day":12,"day_time":7900,"phase":"afternoon","clock":"13:54","moon_phase":3,"is_day":true},"weather":{"rain":false,"thunder":false,"rain_level":0,"thunder_level":0,"seconds_since_weather_change":0}},
             "player":{"name":"MockPlayer","uuid":"00000000-0000-0000-0000-000000000001","pos":[128,70,128],"pos_block":[128,70,128],"dimension":"minecraft:overworld","yaw":0,"pitch":0,"facing":"S","health":20,"max_health":20,"food":20,"air":300,"xp_level":0,"selected_slot":0,"on_ground":true,"sleeping":false,"sprinting":false,"sneaking":false},
             "inventory":{"used":1,"free":35,"aggregate":{"has_food":true,"food_points":30,"torches":0,"low_tools":0}},
             "surroundings":{"biome":"minecraft:meadow","can_see_sky":true,"cave_depth":0,"enclosed_ratio":0,"entity_counts":{"hostile":0,"passive":0}},
             "signals":[],"capabilities":{"server_capture":false,"client_capture":false,"screenshot":false,"static_world_available":false,"static_world_ready":false,"history":false,"chat_write":false,"overlay_write":false,"tts":false,"paused":false},"hashes":{}}
            """).getAsJsonObject();
        JsonArray slots=new JsonArray();
        slots.add(JsonParser.parseString("{\"id\":\"minecraft:bread\",\"count\":6,\"slot\":0,\"enchantments\":[]}"));
        for(int i=1;i<36;i++)slots.add(JsonNull.INSTANCE);
        data.getAsJsonObject("inventory").add("main",slots);
        JsonArray hotbar=new JsonArray();
        for(int i=0;i<9;i++)hotbar.add(slots.get(i).deepCopy());
        data.getAsJsonObject("inventory").add("hotbar",hotbar);
        return Snapshot.fromJson(data);
    }
}

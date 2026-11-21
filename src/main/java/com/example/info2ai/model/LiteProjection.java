package com.example.info2ai.model;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Schema v1 compact profile. Budget only the selected, final representation, never the facts. */
public final class LiteProjection {
    public static final int LITE_MAX_BYTES = 3072;
    private static final Set<String> OMISSION_PATHS=Set.of("signals","session","world","player","inventory","surroundings",
        "player.looking_at","capabilities.static_world_confidence","source.recorded_instance_id","source.world_id","source.coverage_reason",
        "session.world_name","session.seed","surroundings.entity_counts","surroundings.biome","surroundings.light",
        "world.weather","world.time","inventory.aggregate","player.activity","player.held","world.dimension",
        "session.world_id","session.game_mode","session.difficulty","player.screen","player.name","player.uuid","player.facing","player.pos",
        "source.recorded_seq","source.order","source.elapsed_ns","source.recorded_dropped","source.recorded_errors",
        "source.confidence","source.coverage","source.mode");
    private LiteProjection() { }

    /** Unbudgeted profile for HTTP selection; callers must finish after all body metadata edits. */
    public static JsonObject profile(Snapshot snapshot) {
        JsonObject raw=snapshot.toJson(Detail.NORMAL);
        JsonObject out=pick(raw,"v","seq","captured_at","tick","detail","capabilities","source");
        out.addProperty("detail","lite");
        group(out,raw,"session","world_name","world_id","seed","game_mode","difficulty","paused");
        group(out,raw,"world","dimension","time","weather");
        group(out,raw,"player","name","uuid","pos","facing","health","max_health","absorption","food","air","xp_level","screen","confidence");
        JsonObject player=object(out,"player"),rawPlayer=object(raw,"player");
        if(player!=null) {
            group(player,rawPlayer,"activity","kind","confidence","since_s");
            group(player,rawPlayer,"held","id","count","damage","damage_max");
            group(player,rawPlayer,"looking_at","type","id","pos","distance");
        }
        group(out,raw,"inventory","used","free","confidence");
        JsonObject inventory=object(out,"inventory");
        if(inventory!=null)group(inventory,object(raw,"inventory"),"aggregate","has_food","food_points","low_tools","torches","has_light_source","bed");
        group(out,raw,"surroundings","biome","light","can_see_sky","cave_depth","enclosed_ratio","entity_counts","confidence");
        if(object(raw,"source")!=null)group(out,raw,"source","mode","recorded_seq","recorded_instance_id","world_id","order","elapsed_ns","confidence","coverage","coverage_reason","recorded_dropped","recorded_errors");
        JsonObject projection=new JsonObject();projection.addProperty("profile","compact_v1");
        List<Signal> signals=snapshot.signals();
        if(signals==null) {
            out.add("signals",JsonNull.INSTANCE);projection.add("signals_total",JsonNull.INSTANCE);
        } else {
            List<Signal> ordered=new ArrayList<>(signals);
            ordered.sort(Comparator.comparingInt(LiteProjection::priority)); // List.sort is stable.
            JsonArray compact=new JsonArray();
            for(Signal signal:ordered.subList(0,Math.min(6,ordered.size()))) {
                if(signal==null){compact.add(JsonNull.INSTANCE);continue;}
                JsonObject s=pick(signal.toJson(Detail.NORMAL),"kind","severity","since_s");
                if(signal.detail()!=null)group(s,signal.toJson(Detail.NORMAL),"detail","confidence","source");
                compact.add(s);
            }
            out.add("signals",compact);projection.addProperty("signals_total",signals.size());
        }
        projection.add("budget_omitted",new JsonArray());out.add("projection",projection);
        if(snapshot.projection()!=null)restoreCoverage(out,snapshot.projection().toJson(),signals==null?0:signals.size());
        updateSignalCount(out);return out;
    }

    /** Owns its result; actual Gson JSON encoding is exactly the encoding used by HTTP Json.write. */
    public static JsonObject finish(JsonObject selected) {
        JsonObject out=selected.deepCopy();updateSignalCount(out);
        // Values that cannot fit even alone must not displace unrelated small facts.
        for(String path:List.of("player.looking_at","capabilities.static_world_confidence","source.recorded_instance_id","source.world_id","source.coverage_reason",
            "session.world_name","session.seed","surroundings.entity_counts","surroundings.biome","surroundings.light",
            "world.weather","world.time","inventory.aggregate","player.activity","player.held","world.dimension",
            "session.world_id","session.game_mode","session.difficulty","player.screen","player.name","player.uuid","player.facing","player.pos",
            "source.recorded_seq","source.order","source.elapsed_ns","source.recorded_dropped","source.recorded_errors",
            "source.confidence","source.coverage","source.mode")) {
            JsonElement value=valueAt(out,path);
            if(value!=null&&value.toString().getBytes(StandardCharsets.UTF_8).length>LITE_MAX_BYTES) {
                boolean provenance=path.startsWith("source.")&&!List.of("source.recorded_instance_id","source.world_id","source.coverage_reason").contains(path);
                evict(out,path,provenance||path.startsWith("capabilities."));
            }
        }
        JsonElement candidates=out.get("signals");
        if(candidates!=null&&candidates.isJsonArray()) {
            JsonArray list=candidates.getAsJsonArray();
            for(int i=list.size()-1;i>=0;i--)if(list.get(i).toString().getBytes(StandardCharsets.UTF_8).length>LITE_MAX_BYTES) {
                list.remove(i);mark(out,"signals");updateSignalCount(out);
            }
        }
        // Fixed paths keep metadata bounded even when names/IDs/source strings are adversarial.
        for(String path:List.of("player.looking_at","capabilities.static_world_confidence","source.recorded_instance_id","source.world_id","source.coverage_reason",
            "session.world_name","session.seed","surroundings.entity_counts","surroundings","world.weather","world.time",
            "inventory.aggregate","player.activity","player.held")) {
            if(fits(out))return out;
            evict(out,path,path.startsWith("capabilities."));
        }
        // Remove lowest priority retained signals first, with their uncertainty metadata intact.
        JsonElement signals=out.get("signals");
        if(signals!=null&&signals.isJsonArray()) {
            JsonArray list=signals.getAsJsonArray();
            while(!fits(out)&&!list.isEmpty()) {
                list.remove(list.size()-1);mark(out,"signals");updateSignalCount(out);
            }
        }
        for(String path:List.of("signals","world.dimension","session.world_id","session.game_mode","session.difficulty",
            "player.screen","player.name","player.uuid","player.facing","inventory","world","session","capabilities.static_world_confidence","player")) {
            if(fits(out))return out;
            evict(out,path,path.startsWith("capabilities."));
        }
        // Ultimate bounded fallback: keep an explicit source object, and mark unrepresentable provenance unknown.
        // In particular, never silently remove the partial/unknown coverage indicator or imply live capture.
        for(String key:List.of("recorded_seq","order","elapsed_ns","recorded_dropped","recorded_errors","coverage_reason",
            "recorded_instance_id","world_id","confidence","coverage","mode")) {
            if(fits(out))return out;
            evict(out,"source."+key,true);
        }
        if(!fits(out))evict(out,"capabilities.static_world_confidence",true);
        if(!fits(out))throw new IllegalStateException("Lite constant envelope exceeds byte budget");
        return out;
    }
    private static void updateSignalCount(JsonObject out) {
        JsonObject p=out.getAsJsonObject("projection");JsonElement total=p.get("signals_total"),signals=out.get("signals");
        if(total.isJsonNull())p.add("signals_omitted",JsonNull.INSTANCE);
        else p.addProperty("signals_omitted",total.getAsInt()-(signals!=null&&signals.isJsonArray()?signals.getAsJsonArray().size():0));
    }
    private static void restoreCoverage(JsonObject out,JsonObject original,int represented) {
        JsonObject p=out.getAsJsonObject("projection");
        // A compact representation is not a complete capture. Invalid/unknown coverage stays unknown.
        p.add("signals_total",JsonNull.INSTANCE);
        JsonElement profile=original.get("profile"),count=original.get("signals_total");
        try {
            if(profile!=null&&profile.isJsonPrimitive()&&profile.getAsJsonPrimitive().isString()&&"compact_v1".equals(profile.getAsString())
                &&count!=null&&count.isJsonPrimitive()&&count.getAsJsonPrimitive().isNumber()) {
                int total=count.getAsBigDecimal().toBigIntegerExact().intValueExact();
                if(total>=represented)p.addProperty("signals_total",total);
            }
        } catch(ArithmeticException|NumberFormatException malformed) { p.add("signals_total",JsonNull.INSTANCE); }
        JsonElement omitted=original.get("budget_omitted");
        if(omitted!=null&&omitted.isJsonArray())for(JsonElement path:omitted.getAsJsonArray())
            if(path.isJsonPrimitive()&&path.getAsJsonPrimitive().isString()&&OMISSION_PATHS.contains(path.getAsString()))mark(out,path.getAsString());
    }
    private static int priority(Signal s) {
        if(s==null)return 3;
        return "critical".equals(s.severity())?0:"warn".equals(s.severity())?1:"info".equals(s.severity())?2:3;
    }
    private static boolean fits(JsonObject out) {return out.toString().getBytes(StandardCharsets.UTF_8).length<=LITE_MAX_BYTES;}
    private static void evict(JsonObject out,String path,boolean unknown) {
        String[] parts=path.split("\\.");JsonObject parent=parts.length==1?out:object(out,parts[0]);
        String key=parts[parts.length-1];
        if(parent!=null&&parent.has(key)&&!(unknown&&parent.get(key).isJsonNull())) {
            if(unknown)parent.add(key,JsonNull.INSTANCE);else parent.remove(key);
            mark(out,path);updateSignalCount(out);
        }
    }
    private static void mark(JsonObject out,String path) {
        JsonArray omitted=out.getAsJsonObject("projection").getAsJsonArray("budget_omitted");
        if(!omitted.contains(new JsonPrimitive(path)))omitted.add(path);
    }
    private static JsonElement valueAt(JsonObject out,String path) {
        String[] parts=path.split("\\.");JsonObject parent=parts.length==1?out:object(out,parts[0]);
        return parent==null?null:parent.get(parts[parts.length-1]);
    }
    private static JsonObject object(JsonObject o,String key) {return o!=null&&o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):null;}
    private static JsonObject pick(JsonObject o,String... keys) {
        JsonObject result=new JsonObject();
        for(String key:keys)if(o.has(key))result.add(key,o.get(key).deepCopy());return result;
    }
    private static void group(JsonObject out,JsonObject raw,String key,String... keys) {
        JsonObject source=object(raw,key);out.add(key,source==null?JsonNull.INSTANCE:pick(source,keys));
    }
}

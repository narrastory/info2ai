package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.*;

public record Snapshot(
    int v, long seq, long capturedAt, long tick, Detail detail,
    SessionInfo session, WorldInfo world, PlayerInfo player, InventoryInfo inventory,
    SurroundingsInfo surroundings, ProgressInfo progress, HistoryInfo history,
    List<Signal> signals, Capabilities capabilities, Map<String,String> hashes, Facts source, Facts projection, Facts director
) implements JsonModel {
    public Snapshot(int v,long seq,long capturedAt,long tick,Detail detail,SessionInfo session,WorldInfo world,PlayerInfo player,
        InventoryInfo inventory,SurroundingsInfo surroundings,ProgressInfo progress,HistoryInfo history,List<Signal> signals,
        Capabilities capabilities,Map<String,String> hashes,Facts source,Facts projection) {
        this(v,seq,capturedAt,tick,detail,session,world,player,inventory,surroundings,progress,history,signals,capabilities,
            hashes,source,projection,null);
    }
    public Snapshot(int v,long seq,long capturedAt,long tick,Detail detail,SessionInfo session,WorldInfo world,PlayerInfo player,
        InventoryInfo inventory,SurroundingsInfo surroundings,ProgressInfo progress,HistoryInfo history,List<Signal> signals,
        Capabilities capabilities,Map<String,String> hashes,Facts source) {
        this(v,seq,capturedAt,tick,detail,session,world,player,inventory,surroundings,progress,history,signals,capabilities,hashes,source,null);
    }
    public Snapshot(int v,long seq,long capturedAt,long tick,Detail detail,SessionInfo session,WorldInfo world,PlayerInfo player,
        InventoryInfo inventory,SurroundingsInfo surroundings,ProgressInfo progress,HistoryInfo history,List<Signal> signals,
        Capabilities capabilities,Map<String,String> hashes) {
        this(v,seq,capturedAt,tick,detail,session,world,player,inventory,surroundings,progress,history,signals,capabilities,hashes,null);
    }
    public Snapshot {
        if (v != 1) throw new IllegalArgumentException("不支持的 schema: " + v);
        Objects.requireNonNull(detail, "缺少 detail");
        signals = JsonUtil.immutableSlots(signals);
        hashes = hashes == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(hashes));
    }

    public static Snapshot fromJson(JsonObject o) {
        Integer version = JsonUtil.integer(o,"v");
        if (version == null || version != 1) throw new IllegalArgumentException("不支持的 schema: " + version);
        return new Snapshot(version, o.get("seq").getAsLong(), o.get("captured_at").getAsLong(),
            o.get("tick").getAsLong(), Detail.parse(JsonUtil.string(o,"detail")),
            SessionInfo.fromJson(JsonUtil.object(o,"session")), WorldInfo.fromJson(JsonUtil.object(o,"world")),
            PlayerInfo.fromJson(JsonUtil.object(o,"player")), InventoryInfo.fromJson(JsonUtil.object(o,"inventory")),
            SurroundingsInfo.fromJson(JsonUtil.object(o,"surroundings")), ProgressInfo.fromJson(JsonUtil.object(o,"progress")),
            HistoryInfo.fromJson(JsonUtil.object(o,"history")),
            JsonUtil.list(o,"signals",e -> Signal.fromJson(e.getAsJsonObject())),
            Capabilities.fromJson(JsonUtil.object(o,"capabilities")), JsonUtil.stringMap(o,"hashes"),Facts.fromJson(JsonUtil.object(o,"source")),
            Facts.fromJson(JsonUtil.object(o,"projection")),Facts.fromJson(JsonUtil.object(o,"director")));
    }

    @Override public JsonObject toJson() { return toJson(detail); }
    @Override public JsonObject toJson(Detail selected) {
        if (selected == Detail.LITE) return LiteProjection.finish(LiteProjection.profile(this));
        JsonObject o = new JsonObject();
        o.addProperty("v",v);
        o.addProperty("seq",seq);
        o.addProperty("captured_at",capturedAt);
        o.addProperty("tick",tick);
        o.addProperty("detail",selected.jsonName());
        JsonUtil.put(o,"session",session,selected);
        JsonUtil.put(o,"world",world,selected);
        JsonUtil.put(o,"player",player,selected);
        JsonUtil.put(o,"inventory",inventory,selected);
        JsonUtil.put(o,"surroundings",surroundings,selected);
        if (selected != Detail.LITE) JsonUtil.put(o,"progress",progress,selected);
        if (selected == Detail.FULL) JsonUtil.put(o,"history",history,selected);
        JsonUtil.put(o,"signals",signals,selected);
        JsonUtil.put(o,"capabilities",capabilities,selected);
        JsonUtil.put(o,"hashes",hashes,selected);
        if (director != null) JsonUtil.put(o,"director",director,selected);
        if (capabilities != null && Boolean.FALSE.equals(capabilities.serverCapture()) && Boolean.TRUE.equals(capabilities.clientCapture())) {
            for (String key : List.of("player", "inventory", "surroundings"))
                if (o.get(key).isJsonObject()) o.getAsJsonObject(key).addProperty("confidence", "low");
        }
        if(source!=null)JsonUtil.put(o,"source",source,selected);
        return o;
    }

    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private long seq, capturedAt, tick;
        private Detail detail=Detail.NORMAL;
        private SessionInfo session;
        private WorldInfo world;
        private PlayerInfo player;
        private InventoryInfo inventory;
        private SurroundingsInfo surroundings;
        private ProgressInfo progress;
        private HistoryInfo history;
        private List<Signal> signals=List.of();
        private Capabilities capabilities;
        private Map<String,String> hashes=Map.of();
        private Facts director;
        public Builder seq(long value) { seq=value; return this; }
        public Builder capturedAt(long value) { capturedAt=value; return this; }
        public Builder tick(long value) { tick=value; return this; }
        public Builder detail(Detail value) { detail=value; return this; }
        public Builder session(SessionInfo value) { session=value; return this; }
        public Builder world(WorldInfo value) { world=value; return this; }
        public Builder player(PlayerInfo value) { player=value; return this; }
        public Builder inventory(InventoryInfo value) { inventory=value; return this; }
        public Builder surroundings(SurroundingsInfo value) { surroundings=value; return this; }
        public Builder progress(ProgressInfo value) { progress=value; return this; }
        public Builder history(HistoryInfo value) { history=value; return this; }
        public Builder signals(List<Signal> value) { signals=value; return this; }
        public Builder capabilities(Capabilities value) { capabilities=value; return this; }
        public Builder hashes(Map<String,String> value) { hashes=value; return this; }
        public Builder director(Facts value) { director=value; return this; }
        public Snapshot build() { return new Snapshot(1,seq,capturedAt,tick,detail,session,world,player,inventory,surroundings,
            progress,history,signals,capabilities,hashes,null,null,director); }
    }
}

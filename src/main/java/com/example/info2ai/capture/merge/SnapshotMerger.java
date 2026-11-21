package com.example.info2ai.capture.merge;
import com.example.info2ai.model.*;
import com.example.info2ai.util.JsonUtil;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
/** Pure immutable source merge. Minecraft objects never cross this boundary. */
public final class SnapshotMerger {
    private final Map<String,Object> previous=new HashMap<>();
    private final Map<String,String> hashes=new LinkedHashMap<>();
    public synchronized void clear() { previous.clear();hashes.clear(); }
    public synchronized Snapshot merge(Snapshot server,Snapshot client,StaticWorldInfo staticWorld) {
        return merge(server,client,staticWorld,staticWorld!=null);
    }
    public synchronized Snapshot merge(Snapshot server,Snapshot client,StaticWorldInfo staticWorld,boolean staticAvailable) {
        return merge(server,client,staticWorld,staticAvailable,null);
    }
    public synchronized Snapshot merge(Snapshot server,Snapshot client,StaticWorldInfo staticWorld,boolean staticAvailable,String staticConfidence) {
        boolean authoritative=server!=null && server.capabilities()!=null && Boolean.TRUE.equals(server.capabilities().serverCapture());
        boolean clientReady=client!=null && client.capabilities()!=null && Boolean.TRUE.equals(client.capabilities().clientCapture());
        Snapshot base=authoritative?server:clientReady?client:server;
        if(base==null)return null;
        PlayerInfo player=base.player();InventoryInfo inventory=base.inventory();SurroundingsInfo surroundings=base.surroundings();
        if(authoritative && clientReady) {
            if(player!=null && client.player()!=null)player=PlayerInfo.fromJson(overlay(player,client.player(),"screen","looking_at","inputs","view"));
            if(inventory!=null && client.inventory()!=null)inventory=InventoryInfo.fromJson(overlay(inventory,client.inventory(),"cursor"));
            if(surroundings!=null && client.surroundings()!=null)surroundings=SurroundingsInfo.fromJson(overlay(surroundings,client.surroundings(),"sounds","hud","performance"));
        }
        SessionInfo session=base.session();
        if(authoritative && clientReady && session!=null && client.session()!=null)
            session=SessionInfo.fromJson(overlay(session,client.session(),"paused"));
        WorldInfo world=base.world();
        if(world!=null)world=new WorldInfo(world.dimension(),world.dimensionId(),world.time(),world.weather(),world.stats(),staticWorld);
        var cc=clientReady?client.capabilities():null;
        var capabilities=new Capabilities(authoritative,clientReady,cc!=null && Boolean.TRUE.equals(cc.screenshot()),staticAvailable,staticWorld!=null,
            staticWorld==null?staticConfidence:staticWorld.confidence(),base.history()!=null,cc!=null && Boolean.TRUE.equals(cc.chatWrite()),
            cc!=null && Boolean.TRUE.equals(cc.overlayWrite()),cc!=null && Boolean.TRUE.equals(cc.tts()),false,
            base.capabilities()==null?null:base.capabilities().director());
        Map<String,Object> sections=new LinkedHashMap<>();
        sections.put("session",session);sections.put("world",world);sections.put("player",player);sections.put("inventory",inventory);sections.put("surroundings",surroundings);
        sections.put("progress",base.progress());sections.put("history",base.history());sections.put("signals",base.signals());sections.put("capabilities",capabilities);
        if (base.director()!=null) sections.put("director",base.director());
        else { previous.remove("director"); hashes.remove("director"); }
        boolean fallback=!authoritative && clientReady;
        for(var section:sections.entrySet()) {
            String key=section.getKey();Object value=section.getValue();
            // Include source confidence in the hash cache key, without serializing unchanged sections.
            Object comparison=Arrays.asList(value,fallback && Set.of("player","inventory","surroundings").contains(key));
            if(!Objects.equals(previous.get(key),comparison)) {
                // TODO(perf): 总采集实测超预算；分节 JSON/CRC 分配是待 profile 候选，Phase9 前测量归因，不能当作已证实热点。
                var json=JsonUtil.encode(value,Detail.FULL);
                if(fallback && Set.of("player","inventory","surroundings").contains(key) && json.isJsonObject())json.getAsJsonObject().addProperty("confidence","low");
                CRC32 crc=new CRC32();crc.update(json.toString().getBytes(StandardCharsets.UTF_8));
                hashes.put(key,String.format(Locale.ROOT,"%08x",crc.getValue()));previous.put(key,comparison);
            }
        }
        return new Snapshot(1,base.seq(),base.capturedAt(),base.tick(),Detail.FULL,session,world,player,inventory,surroundings,
            base.progress(),base.history(),base.signals(),capabilities,hashes,null,null,base.director());
    }
    private static JsonObject overlay(JsonModel base,JsonModel client,String... fields) {
        JsonObject merged=base.toJson(Detail.FULL),source=client.toJson(Detail.FULL);
        for(String field:fields)if(source.has(field))merged.add(field,source.get(field).deepCopy());
        return merged;
    }
}

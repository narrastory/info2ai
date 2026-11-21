package com.example.info2ai.http.handler;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.http.*;
import com.example.info2ai.model.*;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.CRC32;

public final class StateHandler implements HttpHandler {
    public static final List<String> SECTIONS=List.of("session","world","world.static","player","inventory","surroundings","progress","history","signals","director","hashes");
    private static final Set<String> FIXED=Set.of("v","seq","captured_at","tick","detail","capabilities","source","projection");
    private final Info2AiConfig config;
    private final SnapshotStore store;
    public StateHandler(Info2AiConfig config,SnapshotStore store) { this.config=config;this.store=store; }
    @Override public void handle(HttpExchange exchange) throws IOException {
        if(store.paused())throw new ApiException(503,"paused");
        long requestEpoch=store.worldEpoch();
        Snapshot snapshot=store.current();
        if(snapshot==null || requestEpoch!=store.worldEpoch())throw new ApiException(503,"not_in_world");
        Map<String,String> query=Query.parse(exchange);
        Detail detail;
        try { detail=Detail.parse(query.getOrDefault("detail",config.detail().jsonName())); }
        catch(IllegalArgumentException failure) { throw new ApiException(400,"bad_detail"); }
        Set<String> selected=Query.csv(query.get("sections")),excluded=Query.csv(query.get("exclude"));
        for(String name:union(selected,excluded))if(!SECTIONS.contains(name))throw new ApiException(400,"bad_section",name);
        int max=(int)Query.number(query,"max_entities",config.maxEntities(),0,256);
        Query.number(query,"if_changed",0,0,1);
        if(Query.number(query,"fresh",0,0,1)==1) {
            if(config.mock()&&!store.replay()) { store.publish(MockSnapshotFactory.create());snapshot=store.current(); }
            else {
                var request=store.requestFresh(requestEpoch);
                try {
                    var accepted=request.get(1000,java.util.concurrent.TimeUnit.MILLISECONDS);
                    if(accepted==null || accepted.epoch()!=store.worldEpoch())throw new ApiException(503,"fresh_unavailable");
                    snapshot=accepted.snapshot();
                } catch(java.util.concurrent.TimeoutException timeout) {
                    request.cancel(false);throw new ApiException(504,"fresh_timeout");
                } catch(InterruptedException interrupted) {
                    Thread.currentThread().interrupt();request.cancel(false);throw new ApiException(503,"fresh_interrupted");
                } catch(java.util.concurrent.ExecutionException failure) { throw new ApiException(503,"fresh_failed"); }
            }
        }
        JsonObject body=detail==Detail.LITE?LiteProjection.profile(snapshot):snapshot.toJson(detail);
        if(body.get("capabilities").isJsonObject()) {
            var caps=body.getAsJsonObject("capabilities");
            caps.addProperty("paused",store.paused());
            if(!caps.has("director")) caps.addProperty("director",false);
        }
        for(String key:excluded) {
            if(key.equals("world.static")) { if(body.has("world") && body.get("world").isJsonObject())body.getAsJsonObject("world").remove("static"); }
            else body.remove(key);
        }
        if(body.has("surroundings") && body.get("surroundings").isJsonObject()) {
            JsonObject surroundings=body.getAsJsonObject("surroundings");
            if(surroundings.has("entities") && surroundings.get("entities").isJsonArray()) {
                JsonArray entities=surroundings.getAsJsonArray("entities"),limited=new JsonArray();
                for(int i=0;i<Math.min(max,entities.size());i++)limited.add(entities.get(i).deepCopy());
                surroundings.add("entities",limited);
            }
        }
        if(detail!=Detail.LITE) {
            JsonObject hashes=new JsonObject();
            for(String key:body.keySet())if(!FIXED.contains(key) && !key.equals("hashes")) {
                CRC32 crc=new CRC32();crc.update(body.get(key).toString().getBytes(StandardCharsets.UTF_8));
                hashes.addProperty(key,String.format(Locale.ROOT,"%08x",crc.getValue()));
            }
            if(!excluded.contains("hashes"))body.add("hashes",hashes);
        }
        if(!selected.isEmpty())for(String key:new ArrayList<>(body.keySet()))
            if(!FIXED.contains(key) && !selected.contains(key) && !(key.equals("world") && selected.contains("world.static")))body.remove(key);
        if(!excluded.contains("world") && selected.contains("world.static") && !selected.contains("world")) {
            JsonObject world=body.getAsJsonObject("world"),only=new JsonObject();
            if(world!=null && world.has("static"))only.add("static",world.get("static").deepCopy());
            body.add("world",only);
        }
        if(detail==Detail.LITE)body=LiteProjection.finish(body);
        JsonObject representation=body.deepCopy();
        representation.remove("seq");representation.remove("captured_at");representation.remove("tick");
        String etag;
        try { etag="\""+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(representation.toString().getBytes(StandardCharsets.UTF_8)))+"\""; }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("JDK 缺少 SHA-256",impossible); }
        exchange.getResponseHeaders().set("ETag",etag);
        exchange.getResponseHeaders().set("X-Info2ai-Seq",Long.toString(snapshot.seq()));
        exchange.getResponseHeaders().set("X-Info2ai-RealTime",Long.toString(snapshot.capturedAt()));
        if(query.getOrDefault("if_changed","0").equals("1") && etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match")))Json.write(exchange,304,new JsonObject());
        else Json.write(exchange,200,body);
    }
    private static Set<String> union(Set<String> a,Set<String> b) { Set<String> values=new HashSet<>(a);values.addAll(b);return values; }
}

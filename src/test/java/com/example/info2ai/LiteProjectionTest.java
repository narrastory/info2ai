package com.example.info2ai;

import com.example.info2ai.model.*;
import com.example.info2ai.http.*;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.google.gson.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class LiteProjectionTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    static JsonObject fixture() throws Exception {
        try(var reader=new InputStreamReader(LiteProjectionTest.class.getResourceAsStream("/golden/state_full.json"),StandardCharsets.UTF_8)) {
            JsonObject o=JsonParser.parseReader(reader).getAsJsonObject();
            o.getAsJsonObject("player").addProperty("absorption",2.0);
            o.getAsJsonObject("player").add("activity",JsonParser.parseString("{\"kind\":\"mining\",\"confidence\":\"low\",\"since_s\":12,\"note\":\"uncertain prose\"}"));
            JsonArray hotbar=o.getAsJsonObject("inventory").getAsJsonArray("hotbar");
            JsonObject stack=o.getAsJsonObject("player").getAsJsonObject("held");
            for(int i=0;i<9;i++) hotbar.set(i,stack.deepCopy());
            JsonArray tools=new JsonArray();for(int i=0;i<36;i++)tools.add(stack.deepCopy());
            o.getAsJsonObject("inventory").getAsJsonObject("aggregate").add("tools",tools);
            JsonArray signals=new JsonArray();
            String[] levels={"info","warn","critical","warn","critical","info","critical","warn"};
            for(int i=0;i<levels.length;i++) {
                JsonObject s=new JsonObject();s.addProperty("kind","signal_"+i);s.addProperty("severity",levels[i]);
                s.addProperty("since_s",i);s.addProperty("note","Long human detail ".repeat(80));
                s.add("detail",JsonParser.parseString("{\"confidence\":\"low\",\"source\":\"client\",\"raw\":\"raw details\"}"));signals.add(s);
            }
            o.add("signals",signals);return o;
        }
    }
    static JsonObject pathological() throws Exception {
        JsonObject o=fixture();String huge="中文\"\\\n\t\u0001".repeat(2000);
        o.addProperty("seq",Long.MAX_VALUE);o.addProperty("captured_at",Long.MAX_VALUE);o.addProperty("tick",Long.MAX_VALUE);
        for(String key:List.of("world_name","world_id","seed","game_mode","difficulty"))o.getAsJsonObject("session").addProperty(key,huge);
        o.getAsJsonObject("session").addProperty("paused",true);
        JsonObject p=o.getAsJsonObject("player");p.addProperty("name",huge);p.addProperty("uuid",huge);p.addProperty("screen",huge);
        p.add("pos",JsonParser.parseString("[1.7976931348623157E308,-1.7976931348623157E308,0]"));
        p.getAsJsonObject("held").addProperty("id","custom:"+huge);
        p.getAsJsonObject("activity").addProperty("confidence",huge);
        for(JsonElement s:o.getAsJsonArray("signals"))s.getAsJsonObject().getAsJsonObject("detail").addProperty("source",huge);
        o.getAsJsonObject("world").addProperty("dimension","custom:"+huge);
        o.getAsJsonObject("surroundings").addProperty("biome","custom:"+huge);
        JsonObject source=JsonParser.parseString("{\"mode\":\"replay\",\"coverage\":\"partial\",\"confidence\":\"recorded\",\"recorded_seq\":9223372036854775807,\"order\":9223372036854775807,\"elapsed_ns\":9223372036854775807,\"recorded_dropped\":3,\"recorded_errors\":2}").getAsJsonObject();
        source.addProperty("recorded_instance_id",huge);source.addProperty("world_id",huge);source.addProperty("coverage_reason",huge);o.add("source",source);
        o.getAsJsonObject("capabilities").addProperty("server_capture",false);o.getAsJsonObject("capabilities").addProperty("client_capture",true);
        return o;
    }
    static void bounded(JsonObject o) {
        int bytes=o.toString().getBytes(StandardCharsets.UTF_8).length;
        assertTrue("UTF8 body bytes="+bytes,bytes<=3072);assertEquals(o,JsonParser.parseString(o.toString()));
        assertEquals("compact_v1",o.getAsJsonObject("projection").get("profile").getAsString());
        assertFalse(o.has("hashes"));
    }
    @Test public void ordinaryCompactProfileKeepsUsefulFactsAndPriorityWithoutMutatingDetailedFacts() throws Exception {
        Snapshot s=Snapshot.fromJson(fixture());JsonObject normal=s.toJson(Detail.NORMAL),full=s.toJson(Detail.FULL);
        JsonObject lite=s.toJson(Detail.LITE);bounded(lite);
        assertEquals(2.0,lite.getAsJsonObject("player").get("absorption").getAsDouble(),0);
        assertEquals("low",lite.getAsJsonObject("player").getAsJsonObject("activity").get("confidence").getAsString());
        assertFalse(lite.getAsJsonObject("inventory").has("hotbar"));
        assertFalse(lite.getAsJsonObject("inventory").getAsJsonObject("aggregate").has("tools"));
        assertEquals(43,lite.getAsJsonObject("inventory").getAsJsonObject("aggregate").get("torches").getAsInt());
        assertEquals(List.of("signal_2","signal_4","signal_6","signal_1","signal_3","signal_7"),
            lite.getAsJsonArray("signals").asList().stream().map(e->e.getAsJsonObject().get("kind").getAsString()).toList());
        assertEquals(8,lite.getAsJsonObject("projection").get("signals_total").getAsInt());
        assertEquals(2,lite.getAsJsonObject("projection").get("signals_omitted").getAsInt());
        assertEquals("low",lite.getAsJsonArray("signals").get(0).getAsJsonObject().getAsJsonObject("detail").get("confidence").getAsString());
        assertFalse(lite.getAsJsonArray("signals").get(0).getAsJsonObject().has("note"));
        assertEquals(lite,s.toJson(Detail.LITE));lite.getAsJsonObject("player").addProperty("health",999);
        assertEquals(normal,s.toJson(Detail.NORMAL));assertEquals(full,s.toJson(Detail.FULL));
        System.out.println("LITE ordinary pure bytes="+s.toJson(Detail.LITE).toString().getBytes(StandardCharsets.UTF_8).length);
    }
    @Test public void pathologicalStringsEvictWholeFactsAndCountAllBudgetSignalOmissions() throws Exception {
        Snapshot s=Snapshot.fromJson(pathological());JsonObject before=s.toJson(Detail.FULL),lite=s.toJson(Detail.LITE);bounded(lite);
        assertEquals(Long.MAX_VALUE,lite.get("seq").getAsLong());
        assertEquals("replay",lite.getAsJsonObject("source").get("mode").getAsString());
        assertEquals("partial",lite.getAsJsonObject("source").get("coverage").getAsString());
        assertEquals(3,lite.getAsJsonObject("source").get("recorded_dropped").getAsInt());
        assertEquals(12.0,lite.getAsJsonObject("player").get("health").getAsDouble(),0);
        assertEquals(Double.MAX_VALUE,lite.getAsJsonObject("player").getAsJsonArray("pos").get(0).getAsDouble(),0);
        assertTrue(lite.getAsJsonObject("session").get("paused").getAsBoolean());
        assertFalse(lite.toString().contains("custom:"));
        assertEquals(8-(lite.has("signals")?lite.getAsJsonArray("signals").size():0),lite.getAsJsonObject("projection").get("signals_omitted").getAsInt());
        assertTrue(lite.getAsJsonObject("projection").getAsJsonArray("budget_omitted").size()>0);
        assertEquals(before,s.toJson(Detail.FULL));assertEquals(lite,s.toJson(Detail.LITE));
        if(lite.has("player"))assertEquals("low",lite.getAsJsonObject("player").get("confidence").getAsString());
        if(lite.has("inventory"))assertEquals("low",lite.getAsJsonObject("inventory").get("confidence").getAsString());
        if(lite.has("surroundings"))assertEquals("low",lite.getAsJsonObject("surroundings").get("confidence").getAsString());
        System.out.println("LITE pathological pure bytes="+lite.toString().getBytes(StandardCharsets.UTF_8).length);
    }
    @Test public void unknownSignalsAndPathologicalSourceNeverBecomeKnownEmptyOrLive() throws Exception {
        JsonObject raw=pathological();raw.add("signals",JsonNull.INSTANCE);
        for(String key:List.of("mode","coverage","confidence"))raw.getAsJsonObject("source").addProperty(key,"未知".repeat(5000));
        JsonObject lite=Snapshot.fromJson(raw).toJson(Detail.LITE);bounded(lite);
        assertTrue(lite.getAsJsonObject("projection").get("signals_total").isJsonNull());
        assertTrue(lite.getAsJsonObject("projection").get("signals_omitted").isJsonNull());
        assertTrue(lite.get("signals")==null||lite.get("signals").isJsonNull());
        assertTrue(lite.getAsJsonObject("source").get("mode").isJsonNull());
        assertTrue(lite.getAsJsonObject("source").get("coverage").isJsonNull());
        assertTrue(lite.getAsJsonObject("projection").getAsJsonArray("budget_omitted").contains(new JsonPrimitive("source.mode")));
        assertTrue(lite.getAsJsonObject("projection").getAsJsonArray("budget_omitted").contains(new JsonPrimitive("source.coverage")));
    }
    @Test public void impossibleCapabilityTextCannotDisplaceVitalsOrShortReplayUncertainty() throws Exception {
        JsonObject raw=fixture();raw.getAsJsonObject("capabilities").addProperty("static_world_confidence","未知".repeat(6000));
        raw.add("source",JsonParser.parseString("{\"mode\":\"replay\",\"coverage\":\"unknown\",\"coverage_reason\":\"metadata_missing\",\"confidence\":\"recorded\"}"));
        JsonObject lite=Snapshot.fromJson(raw).toJson(Detail.LITE);bounded(lite);
        assertNotNull("Unrepresentable capability prose must not evict current player facts",lite.getAsJsonObject("player"));
        assertEquals(12.0,lite.getAsJsonObject("player").get("health").getAsDouble(),0);
        assertEquals("00000000-0000-4000-8000-000000000001",lite.getAsJsonObject("player").get("uuid").getAsString());
        assertEquals("replay",lite.getAsJsonObject("source").get("mode").getAsString());
        assertEquals("unknown",lite.getAsJsonObject("source").get("coverage").getAsString());
        assertEquals("metadata_missing",lite.getAsJsonObject("source").get("coverage_reason").getAsString());
        assertTrue(lite.getAsJsonObject("projection").getAsJsonArray("budget_omitted").contains(new JsonPrimitive("capabilities.static_world_confidence")));
    }
    @Test public void reparsingTruncatedLiteCannotClaimTheRepresentedSignalsAreComplete() throws Exception {
        JsonObject raw=fixture();JsonArray all=raw.getAsJsonArray("signals");
        for(int i=8;i<24;i++)all.add(all.get(0).deepCopy());
        JsonObject lite=Snapshot.fromJson(raw).toJson(Detail.LITE);
        assertEquals(24,lite.getAsJsonObject("projection").get("signals_total").getAsInt());
        Snapshot reparsed=Snapshot.fromJson(lite);JsonObject again=reparsed.toJson(Detail.LITE);bounded(again);
        assertEquals(24,again.getAsJsonObject("projection").get("signals_total").getAsInt());
        assertEquals(18,again.getAsJsonObject("projection").get("signals_omitted").getAsInt());
        lite.getAsJsonObject("projection").addProperty("signals_total",6);
        assertEquals(24,reparsed.toJson(Detail.LITE).getAsJsonObject("projection").get("signals_total").getAsInt());
        reparsed.projection().toJson().addProperty("signals_total",6);
        assertEquals(24,reparsed.toJson(Detail.LITE).getAsJsonObject("projection").get("signals_total").getAsInt());
        JsonObject unknown=again.deepCopy();unknown.getAsJsonObject("projection").add("signals_total",JsonNull.INSTANCE);
        assertTrue(Snapshot.fromJson(unknown).toJson(Detail.LITE).getAsJsonObject("projection").get("signals_omitted").isJsonNull());
        JsonObject evicted=Snapshot.fromJson(pathological()).toJson(Detail.LITE);
        JsonObject reEvicted=Snapshot.fromJson(evicted).toJson(Detail.LITE);bounded(reEvicted);
        assertEquals(8,reEvicted.getAsJsonObject("projection").get("signals_total").getAsInt());
        assertEquals(evicted.getAsJsonObject("projection").get("budget_omitted"),reEvicted.getAsJsonObject("projection").get("budget_omitted"));
        JsonObject malformed=again.deepCopy();malformed.getAsJsonObject("projection").add("profile",new JsonObject());
        malformed.getAsJsonObject("projection").getAsJsonArray("budget_omitted").add("opaque_name_".repeat(2000));
        JsonObject safe=Snapshot.fromJson(malformed).toJson(Detail.LITE);bounded(safe);
        assertTrue(safe.getAsJsonObject("projection").get("signals_total").isJsonNull());
        assertFalse(safe.getAsJsonObject("projection").toString().contains("opaque_name_"));
    }
    @Test public void httpMeasuresFinalBytesSelectsBeforeBudgetAndKeepsRepresentationEtags() throws Exception {
        var dir=temp.newFolder().toPath();Files.writeString(dir.resolve("info2ai.json"),"{\"http\":{\"port\":0},\"mock\":false}");
        var config=Info2AiConfig.load(dir);var store=new SnapshotStore(4);store.replayMode();
        Snapshot replay=Snapshot.fromJson(pathological());store.publishRecorded(replay,replay.source());
        Facts smallSource=Facts.fromJson(JsonParser.parseString("{\"mode\":\"replay\",\"coverage\":\"partial\",\"coverage_reason\":\"producer_loss\",\"confidence\":\"recorded\",\"recorded_dropped\":3,\"recorded_errors\":2}").getAsJsonObject());
        try(var host=HttpServerHost.start(config,store)) {
            var client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+host.actualPort();
            var request=HttpRequest.newBuilder(URI.create(base+"/state?detail=lite")).header("Authorization","Bearer "+config.token()).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,response.statusCode());
            assertTrue(response.body().length<=3072);bounded(JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject());
            JsonObject replayBody=JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("replay",replayBody.getAsJsonObject("source").get("mode").getAsString());
            assertEquals("partial",replayBody.getAsJsonObject("source").get("coverage").getAsString());
            assertFalse(replayBody.getAsJsonObject("capabilities").get("server_capture").getAsBoolean());
            assertTrue(replayBody.getAsJsonObject("session").get("paused").getAsBoolean());
            assertFalse(replayBody.getAsJsonObject("capabilities").get("paused").getAsBoolean());
            System.out.println("LITE pathological HTTP bytes="+response.body().length);
            // A huge unrelated player/source signal cannot erase the requested inventory aggregate.
            var selected=client.send(HttpRequest.newBuilder(URI.create(base+"/state?detail=lite&sections=inventory")).header("Authorization","Bearer "+config.token()).build(),HttpResponse.BodyHandlers.ofByteArray());
            JsonObject small=JsonParser.parseString(new String(selected.body(),StandardCharsets.UTF_8)).getAsJsonObject();bounded(small);
            assertEquals(43,small.getAsJsonObject("inventory").getAsJsonObject("aggregate").get("torches").getAsInt());
            assertFalse(small.getAsJsonObject("projection").getAsJsonArray("budget_omitted").toString().contains("inventory"));
            assertFalse(small.getAsJsonObject("projection").getAsJsonArray("budget_omitted").contains(new JsonPrimitive("signals")));
            assertEquals(8,small.getAsJsonObject("projection").get("signals_omitted").getAsInt());
            String tag=selected.headers().firstValue("ETag").orElseThrow();
            JsonObject representation=small.deepCopy();for(String key:List.of("seq","captured_at","tick"))representation.remove(key);
            assertEquals("\""+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(representation.toString().getBytes(StandardCharsets.UTF_8)))+"\"",tag);
            var cached=client.send(HttpRequest.newBuilder(URI.create(base+"/state?detail=lite&sections=inventory&if_changed=1")).header("Authorization","Bearer "+config.token()).header("If-None-Match",tag).build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(304,cached.statusCode());assertEquals(0,cached.body().length);
            var tasks=new ArrayList<CompletableFuture<HttpResponse<byte[]>>>();
            for(String detail:List.of("lite","normal","full","lite","normal","full"))tasks.add(client.sendAsync(HttpRequest.newBuilder(URI.create(base+"/state?detail="+detail)).header("Authorization","Bearer "+config.token()).build(),HttpResponse.BodyHandlers.ofByteArray()));
            for(int i=0;i<tasks.size();i++){var r=tasks.get(i).get(5,TimeUnit.SECONDS);assertEquals(200,r.statusCode());if(i%3==0)assertTrue(r.body().length<=3072);else assertTrue(JsonParser.parseString(new String(r.body(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("inventory").has("hotbar"));}
            assertEquals(400,client.send(HttpRequest.newBuilder(URI.create(base+"/state?detail=lite&sections=invalid")).header("Authorization","Bearer "+config.token()).build(),HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            int largest=0;boolean trimmed=false;
            for(int length=0;length<=600;length+=17) {
                JsonObject varying=fixture();
                for(JsonElement signal:varying.getAsJsonArray("signals"))signal.getAsJsonObject().getAsJsonObject("detail").addProperty("source","中\"\\\n".repeat(length));
                store.publishRecorded(Snapshot.fromJson(varying),smallSource);
                var r=client.send(request,HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,r.statusCode());assertTrue(r.body().length<=3072);largest=Math.max(largest,r.body().length);
                JsonObject tree=JsonParser.parseString(new String(r.body(),StandardCharsets.UTF_8)).getAsJsonObject();bounded(tree);
                JsonArray represented=tree.has("signals")?tree.getAsJsonArray("signals"):new JsonArray();
                assertEquals(8-represented.size(),tree.getAsJsonObject("projection").get("signals_omitted").getAsInt());
                int previous=-1;for(JsonElement e:represented) {
                    JsonObject signal=e.getAsJsonObject();int index=signal.get("since_s").getAsInt();
                    int rank=List.of(2,4,6,1,3,7,0,5).indexOf(index);assertTrue(rank>previous);previous=rank;
                    assertEquals("中\"\\\n".repeat(length),signal.getAsJsonObject("detail").get("source").getAsString());
                    assertEquals("low",signal.getAsJsonObject("detail").get("confidence").getAsString());
                }
                if(represented.size()<6){trimmed=true;assertTrue(tree.getAsJsonObject("projection").getAsJsonArray("budget_omitted").contains(new JsonPrimitive("signals")));}
                if(length==0)System.out.println("LITE ordinary HTTP bytes="+r.body().length);
            }
            assertTrue("Fixture sweep must exercise actual signal budget eviction",trimmed);
            System.out.println("LITE boundary sweep largest HTTP bytes="+largest);
            JsonObject boundary=fixture();boundary.getAsJsonObject("session").addProperty("world_name","");
            store.publishRecorded(Snapshot.fromJson(boundary),smallSource);
            var baseline=client.send(request,HttpResponse.BodyHandlers.ofByteArray());int padding=3072-baseline.body().length;
            String exact="x".repeat(padding);boundary.getAsJsonObject("session").addProperty("world_name",exact);
            store.publishRecorded(Snapshot.fromJson(boundary),smallSource);
            var atLimit=client.send(request,HttpResponse.BodyHandlers.ofByteArray());assertEquals(3072,atLimit.body().length);
            JsonObject exactBody=JsonParser.parseString(new String(atLimit.body(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(exact,exactBody.getAsJsonObject("session").get("world_name").getAsString());
            assertEquals(0,exactBody.getAsJsonObject("projection").getAsJsonArray("budget_omitted").size());
            boundary.getAsJsonObject("session").addProperty("world_name",exact+"x");store.publishRecorded(Snapshot.fromJson(boundary),smallSource);
            var overLimit=client.send(request,HttpResponse.BodyHandlers.ofByteArray());assertTrue(overLimit.body().length<=3072);
            assertTrue(JsonParser.parseString(new String(overLimit.body(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("projection").getAsJsonArray("budget_omitted").size()>0);
            System.out.println("LITE exact-boundary HTTP bytes="+atLimit.body().length+", one-byte-over input bounded to="+overLimit.body().length);
            JsonObject unknown=fixture();unknown.add("signals",JsonNull.INSTANCE);store.publishRecorded(Snapshot.fromJson(unknown),smallSource);
            var r=client.send(request,HttpResponse.BodyHandlers.ofByteArray());bounded(JsonParser.parseString(new String(r.body(),StandardCharsets.UTF_8)).getAsJsonObject());
            assertTrue(JsonParser.parseString(new String(r.body(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("projection").get("signals_total").isJsonNull());
            assertEquals(503,client.send(HttpRequest.newBuilder(URI.create(base+"/state?detail=lite&fresh=1")).header("Authorization","Bearer "+config.token()).build(),HttpResponse.BodyHandlers.ofByteArray()).statusCode());
        } finally {store.close();}
    }
}

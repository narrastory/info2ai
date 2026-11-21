package com.example.info2ai;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.http.MockSnapshotFactory;
import com.example.info2ai.model.*;
import com.example.info2ai.staticworld.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class StaticStoreIntegrationTest {
    @Test public void builtinCapabilityPrecedesResultsAndMetricsFollowCalculations() throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        StaticWorldSource backend = new StaticWorldSource() {
            public boolean isAvailable(StaticQuery query) { return query.supported(); }
            public String availableConfidence() { return "high"; }
            public String describe() { return "builtin"; }
            public StaticWorldInfo getStatic(StaticQuery query) throws InterruptedException {
                release.await();
                var coverage = com.example.info2ai.events.EventData.of("elapsed_ms", 19.2,
                    "chunks_checked", 7, "truncated_by_budget", true);
                return new StaticWorldInfo("builtin", "high", null, null, java.util.List.of(), java.util.List.of(), null, coverage, 123L);
            }
        };
        try (var cache = new CachedStaticWorldSource(backend); var store = new SnapshotStore()) {
            store.staticSource(cache);
            var query = CachedStaticWorldSourceTest.query(0, "minecraft:overworld", 90, 64);
            var pending = store.publishServer(sample(), 0, query).snapshot().capabilities();
            assertTrue(pending.staticWorldAvailable());
            assertFalse(pending.staticWorldReady());
            assertEquals("high", pending.staticWorldConfidence());
            assertFalse(store.staticMetrics().has("elapsed_ms"));
            release.countDown();
            CachedStaticWorldSourceTest.await(() -> store.publishServer(sample(), 0, query).snapshot().capabilities().staticWorldReady());
            assertEquals("builtin", store.current().world().staticWorld().source());
            var metrics = store.staticMetrics();
            assertEquals("builtin", metrics.get("source").getAsString());
            assertEquals(19.2, metrics.get("elapsed_ms").getAsDouble(), 0);
            assertEquals(7, metrics.get("chunks_checked").getAsInt());
            assertTrue(metrics.get("truncated_by_budget").getAsBoolean());
            var disabled = new StaticQuery(0, "world", "0", "1.21.1", query.dimension(), query.generation(),
                90, 64, 0, 2000, false, null);
            var disabledCaps = store.publishServer(sample(), 0, disabled).snapshot().capabilities();
            assertFalse(disabledCaps.staticWorldAvailable());
            assertNull(disabledCaps.staticWorldConfidence());
            store.clearWorld();
            assertFalse(store.staticMetrics().has("elapsed_ms"));
        } finally {
            release.countDown();
        }
    }

    @Test public void staticFieldsSurviveClientRemergeFreshNormalRecordingAndReplay() throws Exception {
        var directory=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("build"),"static-recording-");
        StaticWorldSource backend=new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q){return true;}
            public StaticWorldInfo getStatic(StaticQuery q) {
                var base=CachedStaticWorldSourceTest.data(100);
                return new StaticWorldInfo(base.source(),base.confidence(),null,"bucket position",base.structures(),java.util.List.of(),
                    com.example.info2ai.events.EventData.of("x",64,"y",64,"z",64,"generation","minecraft:normal"),
                    com.example.info2ai.events.EventData.of("absence_proven",false,"truncated",true),123L);
            }
            public String describe(){return "test";}
        };
        com.google.gson.JsonObject expected;
        try(var cache=new CachedStaticWorldSource(backend);var store=new SnapshotStore();
            var recording=new com.example.info2ai.history.HistoryStore(null,store,directory,128)) {
            store.staticSource(cache);var q=CachedStaticWorldSourceTest.query(0,"minecraft:overworld",90,64);
            CachedStaticWorldSourceTest.await(()-> {store.publishServer(sample(),0,q);return Boolean.TRUE.equals(store.current().capabilities().staticWorldReady());});
            var client=sample().toJson(Detail.FULL);client.getAsJsonObject("capabilities").addProperty("server_capture",false);
            client.getAsJsonObject("capabilities").addProperty("client_capture",true);
            long before=store.current().seq();store.publishClient(Snapshot.fromJson(client));
            CachedStaticWorldSourceTest.await(()->store.current().seq()>before);
            assertTrue(store.current().capabilities().staticWorldReady());assertNotNull(store.current().world().staticWorld());
            store.enableFresh(true);var fresh=store.requestFresh();var pending=store.takeFreshRequests();
            var accepted=store.publishServer(sample(),0,q);store.completeFresh(pending,accepted);
            assertSame(accepted,fresh.get(1,java.util.concurrent.TimeUnit.SECONDS));
            expected=accepted.snapshot().world().staticWorld().toJson(Detail.NORMAL);recording.flush();
            var normal=accepted.snapshot().toJson(Detail.NORMAL);
            int withStatic=normal.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            normal.getAsJsonObject("world").add("static",com.google.gson.JsonNull.INSTANCE);
            System.out.println("PHASE6 one_structure_fixture static_field_added_bytes="+(withStatic-normal.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
            var row=com.google.gson.JsonParser.parseString(java.nio.file.Files.readAllLines(directory.resolve("snapshots.jsonl")).getLast()).getAsJsonObject();
            assertEquals(expected,row.getAsJsonObject("payload").getAsJsonObject("world").getAsJsonObject("static"));
        }
        try(var store=new SnapshotStore();var replay=new com.example.info2ai.history.ReplaySnapshotSource(directory,store)) {
            replay.start();CachedStaticWorldSourceTest.await(replay::finished);
            assertEquals("finished",store.sourceStatus());assertEquals(expected,store.current().world().staticWorld().toJson(Detail.NORMAL));
            assertFalse(store.current().capabilities().staticWorldAvailable());assertTrue(store.staticMetrics().entrySet().isEmpty());
        }
    }

    static Snapshot sample() {
        var json=MockSnapshotFactory.create().toJson(Detail.FULL);
        var session=json.getAsJsonObject("session");session.addProperty("world_id","world");session.addProperty("seed","0");session.addProperty("mc_version","1.21.1");
        json.getAsJsonObject("world").addProperty("dimension_id","minecraft:overworld");
        json.getAsJsonObject("player").add("pos",com.google.gson.JsonParser.parseString("[90,64,0]"));
        json.getAsJsonObject("capabilities").addProperty("server_capture",true);
        return Snapshot.fromJson(json);
    }
    @Test public void onlyServerCaptureMergesMatchingCacheAndNeverCompletesFreshByItself() throws Exception {
        StaticWorldSource backend=new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q){return true;}
            public StaticWorldInfo getStatic(StaticQuery q){return CachedStaticWorldSourceTest.data(100);}
            public String describe(){return "test";}
        };
        try(var cache=new CachedStaticWorldSource(backend);var store=new SnapshotStore()) {
            store.staticSource(cache);var q=CachedStaticWorldSourceTest.query(0,"minecraft:overworld",90,64);
            store.publishServer(sample(),0,q);store.enableFresh(true);var fresh=store.requestFresh();
            CachedStaticWorldSourceTest.await(()-> {store.publishServer(sample(),0,q);return Boolean.TRUE.equals(store.current().capabilities().staticWorldReady());});
            assertEquals(10d,store.current().world().staticWorld().structures().getFirst().distance(),0);assertFalse(fresh.isDone());
            var invalid=new StaticQuery(0,"another","0","1.21.1","minecraft:overworld","minecraft:normal",90,64,0,2000,true,q.context());
            store.publishServer(sample(),0,invalid);assertNull(store.current().world().staticWorld());
            assertFalse(store.current().capabilities().staticWorldReady());
            store.clearWorld();assertNull(store.current());
        }
    }
}

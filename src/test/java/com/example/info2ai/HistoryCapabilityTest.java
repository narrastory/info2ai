package com.example.info2ai;

import com.example.info2ai.capture.merge.*;
import com.example.info2ai.history.HistoryStore;
import com.example.info2ai.http.MockSnapshotFactory;
import com.example.info2ai.model.*;
import com.google.gson.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class HistoryCapabilityTest {
    private static Snapshot source(boolean client) {
        var o=PersistentHistoryTest.sample(PersistentHistoryTest.A,1).toJson(Detail.FULL);
        o.remove("history");var caps=o.getAsJsonObject("capabilities");
        caps.addProperty("server_capture",!client);caps.addProperty("client_capture",client);
        caps.addProperty("screenshot",client);caps.addProperty("chat_write",client);caps.addProperty("overlay_write",client);
        return Snapshot.fromJson(o);
    }
    @Test public void persistentHistoryCapabilitySurvivesServerClientFreshAndRecordingPipeline() throws Exception {
        Path disk=PersistentHistoryTest.directory("history-capability-");
        Path record=PersistentHistoryTest.directory("history-capability-record-");
        try(var store=new SnapshotStore();var history=new HistoryStore(disk,store,record,64)) {
            Snapshot server=source(false),client=source(true);assertNull(server.history());
            store.publishServer(server);history.flush();
            var ready=store.publishServer(server);
            assertEquals("ready",ready.snapshot().history().memory().toJson().get("status").getAsString());
            assertTrue("ready persistent history must be advertised",ready.snapshot().capabilities().history());
            assertSame(ready.snapshot(),store.current());
            long before=store.current().seq();store.publishClient(client);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(store.current().seq()==before&&System.nanoTime()<deadline)Thread.sleep(5);
            assertTrue(store.current().seq()>before);assertTrue(store.current().capabilities().history());
            var expected=new SnapshotMerger().merge(server,client,null).capabilities().toJson();expected.addProperty("history",true);
            assertEquals("only history capability is corrected",expected,store.current().capabilities().toJson());
            store.enableFresh(true);var fresh=store.requestFresh();var pending=store.takeFreshRequests();
            var accepted=store.publishServer(server);store.completeFresh(pending,accepted);
            assertSame(accepted,fresh.get(1,TimeUnit.SECONDS));assertSame(accepted.snapshot(),store.current());
            assertTrue(accepted.snapshot().capabilities().history());
            assertEquals("ready",accepted.snapshot().history().memory().toJson().get("status").getAsString());
            history.flush();
            var rows=Files.readAllLines(record.resolve("snapshots.jsonl"));
            var recorded=JsonParser.parseString(rows.getLast()).getAsJsonObject().getAsJsonObject("payload");
            assertEquals(accepted.snapshot().seq(),recorded.get("seq").getAsLong());
            assertEquals(accepted.snapshot().capabilities().toJson(),recorded.getAsJsonObject("capabilities"));
            assertTrue(recorded.getAsJsonObject("capabilities").get("history").getAsBoolean());
        }
    }
    @Test public void noBackendRecordOnlyMockReplayAndLegacyHistoryKeepHonestCapabilities() throws Exception {
        try(var store=new SnapshotStore()) {
            store.publishServer(source(false));assertNull(store.current().history());assertFalse(store.current().capabilities().history());
            var legacy=source(false).toJson(Detail.FULL);
            legacy.add("history",new HistoryInfo(0,0,null,null,null,null,null,null,null).toJson());
            store.publish(Snapshot.fromJson(legacy));
            assertTrue("a valid legacy history contribution must remain available",store.current().capabilities().history());
        }
        try(var store=new SnapshotStore();var recorder=new HistoryStore(null,store,PersistentHistoryTest.directory("record-only-capability-"),16)) {
            store.publish(MockSnapshotFactory.create());recorder.flush();
            assertFalse(recorder.persistent());assertNull(store.current().history());assertFalse(store.current().capabilities().history());
        }
        try(var store=new SnapshotStore()) {
            store.replayMode();store.publishRecorded(MockSnapshotFactory.create(),com.example.info2ai.events.EventData.of("mode","replay"));
            assertNull(store.current().history());assertFalse(store.current().capabilities().history());
        }
    }
}

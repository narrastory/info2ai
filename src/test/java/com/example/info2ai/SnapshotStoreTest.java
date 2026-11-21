package com.example.info2ai;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.http.MockSnapshotFactory;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class SnapshotStoreTest {
    @Test public void clientHandoffNeverWaitsForServerMergeLock() throws Exception {
        try (var store = new SnapshotStore(); var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var sample = MockSnapshotFactory.create();
            synchronized (store) {
                threads.submit(() -> store.publishClient(sample)).get(300, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Test public void invalidationHidesWorldImmediatelyAndRejectsQueuedOldClientAndServer() throws Exception {
        try (var store = new SnapshotStore()) {
            var sample = source(true,false);
            store.publishServer(sample);
            long oldEpoch = store.worldEpoch();
            synchronized (store) {
                store.publishClient(sample, oldEpoch);
                store.invalidateWorld(oldEpoch);
                assertNull(store.current());
            }
            store.publishServer(sample, oldEpoch);
            assertNull(store.current());
            store.publishServer(sample, store.worldEpoch());
            long seq = store.current().seq();
            store.publishClient(sample, oldEpoch);
            Thread.sleep(150);
            assertEquals(seq, store.current().seq());
            assertFalse(store.current().capabilities().clientCapture());
        }
    }

    @Test public void clientPublicationCannotCompleteServerFreshRequest() throws Exception {
        try (var store = new SnapshotStore()) {
            var sample = source(true,false);
            store.publishServer(sample); store.enableFresh(true);
            var fresh = store.requestFresh();
            long before = store.current().seq(); store.publishClient(source(false,true));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (store.current().seq() == before && System.nanoTime() < deadline) Thread.sleep(5);
            assertTrue(store.current().seq() > before);
            assertTrue(store.current().capabilities().clientCapture());
            assertFalse(fresh.isDone()); assertEquals(1, store.takeFreshRequests().size());
        }
    }

    @Test public void unknownWorldIdentityCannotBePublishedAsClientFacts() throws Exception {
        try (var store=new SnapshotStore()) {
            var server=source(true,false).toJson(); var client=source(false,true).toJson();
            server.getAsJsonObject("session").remove("world_id"); client.getAsJsonObject("session").remove("world_id");
            store.publishServer(com.example.info2ai.model.Snapshot.fromJson(server));
            long seq=store.current().seq(); store.publishClient(com.example.info2ai.model.Snapshot.fromJson(client));
            Thread.sleep(150);
            assertEquals(seq,store.current().seq()); assertFalse(store.current().capabilities().clientCapture());
        }
    }
    @Test public void rejectedServerPublicationCannotEmitOrRecordMetricsInReplacementEpoch() {
        try(var store=new SnapshotStore()) {
            var accepted=store.publishServer(source(true,false));long old=store.worldEpoch();
            assertNotNull(accepted);assertEquals(store.current().seq(),accepted.snapshot().seq());
            store.invalidateWorld(old);assertNull(store.publishServer(source(true,false),old));
            store.publishServer(source(true,false),store.worldEpoch());long seq=store.sequence();
            assertFalse(store.emit(old,"world_loaded","info","旧世界"));
            store.recordServerCapture(old,9999);store.recordClientCapture(old,9999);store.recordScreenshot(old,9999);
            assertEquals(seq,store.sequence());assertEquals(0,store.perfStats().snapshot().samples());
            assertEquals(0,store.clientPerfStats().snapshot().samples());assertEquals(0,store.screenshotPerfStats().snapshot().samples());
            long currentEpoch=store.worldEpoch();store.recordServerCapture(currentEpoch,100);store.recordScreenshot(currentEpoch,200);
            store.recordServerCapture(old,9999);store.recordScreenshot(old,9999);
            assertEquals(1,store.perfStats().snapshot().samples());assertEquals(0.1,store.perfStats().snapshot().avgMs(),0);
            assertEquals(1,store.screenshotPerfStats().snapshot().samples());assertEquals(0.2,store.screenshotPerfStats().snapshot().avgMs(),0);
        }
    }

    private static com.example.info2ai.model.Snapshot source(boolean server,boolean client) {
        var value=MockSnapshotFactory.create().toJson();
        value.getAsJsonObject("capabilities").addProperty("server_capture",server);
        value.getAsJsonObject("capabilities").addProperty("client_capture",client);
        return com.example.info2ai.model.Snapshot.fromJson(value);
    }

    @Test public void externallyVisibleCursorNeverExposesUnpublishedEvent() throws Exception {
        SnapshotStore store=new SnapshotStore();
        ExecutorService reader=Executors.newVirtualThreadPerTaskExecutor();
        Thread publisher=Thread.ofPlatform().unstarted(()->store.emit("test","info","test"));
        try {
            // 暂停实际的环形缓冲写入，观察公开游标是否泄露尚未发布的事件。
            synchronized(store.events()) {
                publisher.start();
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                while(publisher.getState()!=Thread.State.BLOCKED && System.nanoTime()<deadline)Thread.onSpinWait();
                assertEquals(Thread.State.BLOCKED,publisher.getState());
                Future<Long> watermark=reader.submit(store::sequence);
                try { assertEquals(0L,watermark.get(100,TimeUnit.MILLISECONDS).longValue()); }
                catch(TimeoutException waitsForPublication) { /* 等待提交也是正确行为。 */ }
            }
            publisher.join(2000);assertFalse(publisher.isAlive());
            assertEquals(1,store.sequence());
        } finally { publisher.join(2000);reader.shutdownNow();store.close(); }
    }

    @Test public void unloadStateIsPublishedBeforeWaiterWakeup() {
        SnapshotStore store=new SnapshotStore();store.publish(MockSnapshotFactory.create());
        AtomicReference<Boolean> observed=new AtomicReference<>();
        store.polls().register(store.sequence(),1000).thenRun(()->observed.set(store.current()!=null));
        store.clearWorld();
        assertEquals(Boolean.FALSE,observed.get());
        assertEquals("world_unloaded",store.events().read(0,10,java.util.Set.of()).events().getFirst().kind());
    }
}

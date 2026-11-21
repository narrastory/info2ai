package com.example.info2ai;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.history.HistoryStore;
import com.example.info2ai.events.EventDraft;
import com.example.info2ai.events.EventData;
import com.example.info2ai.http.MockSnapshotFactory;
import com.example.info2ai.model.*;
import com.google.gson.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class PersistentHistoryTest {
    static final String A="a".repeat(64), B="b".repeat(64);
    static Path directory(String prefix) throws Exception { return Files.createTempDirectory(Path.of("build"),prefix); }
    static Snapshot sample(String id,long tick) {
        var o=MockSnapshotFactory.create().toJson(Detail.FULL);
        o.getAsJsonObject("session").addProperty("world_id",id);
        o.addProperty("tick",tick);
        o.getAsJsonObject("player").add("activity",EventData.of("kind","mining","confidence",.8).toJson());
        return Snapshot.fromJson(o);
    }
    static void death(SnapshotStore store) {
        assertTrue(store.emit(store.worldEpoch(),new EventDraft(12,"death","critical","死亡",EventData.of("cause","minecraft:generic_kill"),List.of(1d,2d,3d),0)));
    }
    @Test public void cumulativeThreeSessionsAndSameNameWorldIsolation() throws Exception {
        Path dir=directory("history-three-");
        for(int session=1;session<=3;session++) {
            try(var store=new SnapshotStore();var history=new HistoryStore(dir,store,null,128)) {
                store.publish(sample(A,1));death(store);history.flush();
                var facts=history.snapshot(A).toJson();
                assertEquals(session,facts.getAsJsonObject("memory").get("cumulative_deaths").getAsInt());
                assertEquals(1,facts.get("session_events").getAsInt());
                assertEquals(1,facts.get("deaths_this_session").getAsInt());
                if(session==1)assertTrue(facts.get("previous_session").isJsonNull());
                else {
                    assertEquals(session,facts.getAsJsonObject("previous_session").get("sessions_count").getAsInt());
                    assertEquals("mining",facts.getAsJsonObject("previous_session").get("last_activity").getAsString());
                }
                store.clearWorld();history.flush();
            }
        }
        try(var store=new SnapshotStore();var history=new HistoryStore(dir,store,null,128)) {
            store.publish(sample(B,1));history.flush();
            assertEquals(0,history.snapshot(B).toJson().getAsJsonObject("memory").get("cumulative_deaths").getAsInt());
            assertNull(history.snapshot(B).previousSession());
        }
        try(var files=Files.list(dir.resolve(A))) {
            for(Path file:files.filter(p->p.toString().endsWith("jsonl")).toList())
                for(String line:Files.readAllLines(file))assertTrue(JsonParser.parseString(line).getAsJsonObject().has("type"));
        }
    }
    @Test public void corruptCheckpointAndTruncatedTailRecoverWithoutClaimingKnownPast() throws Exception {
        Path dir=directory("history-corrupt-");
        try(var store=new SnapshotStore();var history=new HistoryStore(dir,store,null,128)) {
            store.publish(sample(A,1));death(store);store.clearWorld();history.flush();
        }
        Files.writeString(dir.resolve(A).resolve("checkpoint.json"),"{broken");
        Path log;try(var files=Files.list(dir.resolve(A))) { log=files.filter(p->p.toString().endsWith("jsonl")).findFirst().orElseThrow(); }
        Files.writeString(log,"{truncated",StandardOpenOption.APPEND);
        try(var store=new SnapshotStore();var history=new HistoryStore(dir,store,null,128)) {
            store.publish(sample(A,2));history.flush();
            var memory=history.snapshot(A).toJson().getAsJsonObject("memory");
            assertEquals(1,memory.get("cumulative_deaths").getAsInt());
            assertEquals("partial",memory.get("status").getAsString());
            assertFalse(memory.get("pre_observation_known").getAsBoolean());
            assertNotNull(history.snapshot(A).previousSession());
            assertFalse(store.warnings().isEmpty());
        }
    }
    @Test public void failuresAndPressureAreBoundedAndVisible() throws Exception {
        Path root=directory("history-fail-");Path file=root.resolve("not-directory");Files.writeString(file,"x");
        try(var store=new SnapshotStore();var history=new HistoryStore(file,store,null,1)) {
            long start=System.nanoTime();store.publish(sample(A,1));
            for(int i=0;i<10000;i++)store.emit("test","info","test");
            assertTrue("offers blocked",System.nanoTime()-start<3_000_000_000L);
            history.flush();
            assertTrue(history.metrics().get("dropped").getAsLong()>0);
            assertTrue(history.metrics().get("failures").getAsLong()>0);
            assertEquals("unavailable",history.snapshot(A).memory().toJson().get("status").getAsString());
            assertFalse(store.warnings().isEmpty());
            assertEquals(10000,store.totalEvents());
        }
    }
    @Test public void lifecycleLaneSurvivesDataPressureAndSameWorldReentry() throws Exception {
        Path dir=directory("history-lifecycle-");
        try(var store=new SnapshotStore();var history=new HistoryStore(dir,store,null,1)) {
            store.publish(sample(A,1));history.flush();
            for(int i=0;i<10000;i++)store.emit("test","info","test");
            store.clearWorld();history.flush();
            store.publish(sample(A,2));history.flush();
            assertEquals(2,history.snapshot(A).memory().toJson().get("sessions_count").getAsInt());
            store.clearHistory();history.flush();
            assertEquals(0,store.historyEvents().size());assertEquals(0,store.events().size());
            assertNotNull(history.snapshot(A).previousSession());
        }
        long unloads=0;
        try(var logs=Files.list(dir.resolve(A))) {
            for(Path log:logs.filter(p->p.toString().endsWith("jsonl")).toList())
                unloads+=Files.readAllLines(log).stream().filter(s->s.contains("world_unloaded")).count();
        }
        assertEquals(1,unloads);
    }
    @Test public void observedBiomeMemoryIsScopedAndCorruptFilesArePreserved() throws Exception {
        Path dir=directory("history-biomes-");
        try(var store=new SnapshotStore();var history=new HistoryStore(dir,store,null,16)) {
            store.publish(sample(A,1));history.flush();
            String biome=store.current().surroundings().biome();
            assertEquals(Boolean.TRUE,history.biomeMemory(A,biome).observed());
            assertFalse(history.biomeMemory(A,biome).absoluteFirstKnown());
            assertNull(history.biomeMemory(B,biome).observed());
        }
        Files.writeString(dir.resolve(A).resolve("checkpoint.json"),"{broken");
        try(var store=new SnapshotStore();var history=new HistoryStore(dir,store,null,16)) {
            store.publish(sample(A,2));history.flush();
        }
        try(var files=Files.list(dir.resolve(A))) {
            assertTrue(files.anyMatch(p->p.getFileName().toString().startsWith("checkpoint.corrupt-")));
        }
    }
    @Test public void duplicateLogRowsDoNotDoubleCountAndThirtyPositionWindowIsBounded() throws Exception {
        Path dir=directory("history-window-");
        try(var store=new SnapshotStore();var history=new HistoryStore(dir,store,null,128)) {
            for(int i=0;i<40;i++) {
                var o=sample(A,i*200).toJson(Detail.FULL);o.addProperty("seq",i+1);o.addProperty("captured_at",1_000_000L+i*10000);
                history.offer(new com.example.info2ai.history.Commit(i+1,i*10000000L,Snapshot.fromJson(o),null));
            }
            history.flush();assertEquals(30,history.snapshot(A).recentPositions().size());
        }
        Path deaths=directory("history-duplicate-");
        try(var store=new SnapshotStore();var history=new HistoryStore(deaths,store,null,16)) {
            store.publish(sample(A,1));death(store);history.flush();
        }
        Path log;try(var files=Files.list(deaths.resolve(A))) {log=files.filter(p->p.toString().endsWith("jsonl")).findFirst().orElseThrow();}
        String row=Files.readAllLines(log).stream().filter(s->s.contains("\"death\"")).findFirst().orElseThrow();
        Files.writeString(log,row+"\n",StandardOpenOption.APPEND);
        Files.delete(deaths.resolve(A).resolve("checkpoint.json"));
        try(var store=new SnapshotStore();var history=new HistoryStore(deaths,store,null,16)) {
            store.publish(sample(A,1));history.flush();
            assertEquals(1,history.snapshot(A).memory().toJson().get("cumulative_deaths").getAsInt());
            assertEquals("partial",history.snapshot(A).memory().toJson().get("status").getAsString());
        }
    }
    @Test public void historyKeepsDimensionSchemaAndAuthoritativeDeathCause() throws Exception {
        try(var store=new SnapshotStore();var history=new HistoryStore(directory("history-schema-"),store,null,16)) {
            store.publish(sample(A,1));death(store);
            store.emit(store.worldEpoch(),new EventDraft(13,"dimension_changed","info","进入末地",
                EventData.of("dimension","minecraft:the_end","day",33),List.of(1d,2d,3d),0));history.flush();
            var change=history.snapshot(A).dimensionSwitches().getFirst().toJson();
            assertEquals("minecraft:the_end",change.get("to").getAsString());
            assertEquals(33,change.get("day").getAsInt());assertTrue(change.get("ago_s").getAsLong()>=0);
            assertEquals("minecraft:generic_kill",history.snapshot(A).lastDeath().toJson().get("cause").getAsString());
        }
    }
}

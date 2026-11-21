package com.example.info2ai;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.history.*;
import com.example.info2ai.http.*;
import com.google.gson.*;
import org.junit.Test;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.Assert.*;

public class ReplayRoundTripTest {
    @Test public void actualRecordingReplaysMergedOrderOriginalTimesAndHttp() throws Exception {
        Path dir=PersistentHistoryTest.directory("record-roundtrip-");
        long captured,eventTime,originalSeq;
        try(var original=new SnapshotStore();var recorder=new HistoryStore(null,original,dir,128)) {
            original.publish(PersistentHistoryTest.sample(PersistentHistoryTest.A,17));
            captured=original.current().capturedAt();
            Thread.sleep(70);
            PersistentHistoryTest.death(original);
            var event=original.events().read(0,10,Set.of()).events().getFirst();eventTime=event.t();originalSeq=event.seq();
            Thread.sleep(70);
            original.publish(PersistentHistoryTest.sample(PersistentHistoryTest.A,18));recorder.flush();
        }
        assertEquals(2,Files.readAllLines(dir.resolve("snapshots.jsonl")).size());
        assertEquals(1,Files.readAllLines(dir.resolve("events.jsonl")).size());
        Path configDir=PersistentHistoryTest.directory("replay-http-");
        Files.writeString(configDir.resolve("info2ai.json"),"{\"http\":{\"port\":0}}");
        var config=Info2AiConfig.load(configDir);
        try(var store=new SnapshotStore();var replay=new ReplaySnapshotSource(dir,store);var host=HttpServerHost.start(config,store)) {
            replay.start();
            long deadline=System.nanoTime()+5_000_000_000L;
            while(!replay.finished()&&System.nanoTime()<deadline)Thread.sleep(10);
            assertTrue(replay.finished());assertEquals("finished",store.sourceStatus());
            var state=HistoryRoutesTest.get(host,config,"/state");
            assertEquals(18,state.get("tick").getAsLong());
            assertEquals("replay",state.getAsJsonObject("source").get("mode").getAsString());
            assertFalse(state.getAsJsonObject("capabilities").get("server_capture").getAsBoolean());
            assertFalse(state.getAsJsonObject("capabilities").get("client_capture").getAsBoolean());
            var events=HistoryRoutesTest.get(host,config,"/events/history").getAsJsonArray("events");
            assertEquals(1,events.size());
            var event=events.get(0).getAsJsonObject();
            assertEquals(eventTime,event.get("t").getAsLong());assertEquals(12,event.get("tick").getAsLong());
            assertEquals(originalSeq,event.getAsJsonObject("data").getAsJsonObject("replay").get("recorded_seq").getAsLong());
            assertTrue(event.get("seq").getAsLong()<state.get("seq").getAsLong());
            assertEquals(3,store.sequence());
            var fresh=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+host.actualPort()+"/state?fresh=1"))
                .header("Authorization","Bearer "+config.token()).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(503,fresh.statusCode());assertTrue(fresh.body().contains("fresh_unavailable"));
            assertTrue(HistoryRoutesTest.get(host,config,"/health").get("replay").getAsBoolean());
        }
        var first=JsonParser.parseString(Files.readAllLines(dir.resolve("snapshots.jsonl")).getFirst()).getAsJsonObject();
        assertEquals(captured,first.getAsJsonObject("payload").get("captured_at").getAsLong());
        assertEquals("normal",first.getAsJsonObject("payload").get("detail").getAsString());
        assertFalse(first.getAsJsonObject("payload").has("history"));
    }
    @Test public void invalidReplayAndExistingRecordingsNeverFallBackOrOverwrite() throws Exception {
        Path dir=PersistentHistoryTest.directory("record-protect-");
        Files.writeString(dir.resolve("snapshots.jsonl"),"original");Files.writeString(dir.resolve("events.jsonl"),"{}");
        try(var store=new SnapshotStore();var recorder=new HistoryStore(null,store,dir,8)) {
            store.publish(PersistentHistoryTest.sample(PersistentHistoryTest.A,1));recorder.flush();
            assertFalse(store.warnings().isEmpty());
            assertEquals("original",Files.readString(dir.resolve("snapshots.jsonl")));
        }
        try(var store=new SnapshotStore();var replay=new ReplaySnapshotSource(dir,store)) {
            replay.start();long deadline=System.nanoTime()+3_000_000_000L;
            while(!replay.finished()&&System.nanoTime()<deadline)Thread.sleep(10);
            assertEquals("unavailable",store.sourceStatus());assertNull(store.current());
            assertFalse(store.warnings().isEmpty());
        }
    }
    @Test public void standaloneMainLoadsNoMinecraftAndServesRecordedState() throws Exception {
        Path dir=PersistentHistoryTest.directory("standalone-record-");
        try(var store=new SnapshotStore();var recording=new HistoryStore(null,store,dir,16)) {
            store.publish(PersistentHistoryTest.sample(PersistentHistoryTest.A,33));recording.flush();
        }
        Path configDir=PersistentHistoryTest.directory("standalone-config-");
        Files.writeString(configDir.resolve("info2ai.json"),"{\"http\":{\"port\":0}}");
        var config=Info2AiConfig.load(configDir);
        Path classes=configDir.resolve("classes.log");
        String javaExecutable=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        Process process=new ProcessBuilder(javaExecutable,"-Xlog:class+load=info:file="+classes.toString().replace('\\','/'),
            "-Dinfo2ai.configDir="+configDir.toAbsolutePath(),"-Dinfo2ai.replay="+dir.toAbsolutePath(),
            "-cp",System.getProperty("info2ai.testRuntimeClasspath"),"com.example.info2ai.http.ReplayMain")
            .redirectError(configDir.resolve("stderr.log").toFile()).start();
        try {
            var line=java.util.concurrent.CompletableFuture.supplyAsync(()-> {
                try {return process.inputReader().readLine();}catch(Exception e){throw new RuntimeException(e);}
            }).get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(line,line.startsWith("info2ai REPLAY: http://127.0.0.1:"));
            String base=line.substring("info2ai REPLAY: ".length());
            HttpResponse<String> response=null;
            for(int i=0;i<100;i++) {
                response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base+"/state"))
                    .header("Authorization","Bearer "+config.token()).build(),HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()==200)break;Thread.sleep(20);
            }
            assertEquals(200,response.statusCode());
            assertEquals(33,JsonParser.parseString(response.body()).getAsJsonObject().get("tick").getAsLong());
            assertFalse(Files.exists(configDir.resolve("info2ai/history")));
        } finally {process.destroy();if(!process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS))process.destroyForcibly();}
        assertFalse(Files.readString(classes).contains(" net.minecraft."));
        assertFalse(Files.readString(classes).contains(" net.fabricmc."));
    }
}

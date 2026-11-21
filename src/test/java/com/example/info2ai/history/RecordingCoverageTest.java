package com.example.info2ai.history;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.http.*;
import com.google.gson.*;
import org.junit.Test;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class RecordingCoverageTest {
    private static Path directory(String prefix) throws Exception {return Files.createTempDirectory(Path.of("build"),prefix);}
    private static JsonObject json(Path path) throws Exception {return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    private static Path recording() throws Exception {
        Path dir=directory("coverage-record-");
        try(var store=new SnapshotStore();var history=new HistoryStore(null,store,dir,16)) {
            store.publish(MockSnapshotFactory.create());store.emit("retained","info","retained");
            store.publish(MockSnapshotFactory.create());history.flush();
        }
        return dir;
    }
    @Test public void healthyClosePersistsValidatedCompleteCoverage() throws Exception {
        Path dir=recording();
        assertEquals("complete",json(dir.resolve("recording.json")).get("state").getAsString());
        assertEquals("complete",replay(dir,"complete").getAsJsonObject("source").get("coverage").getAsString());
    }
    @Test public void validPrefixTruncationCannotReuseCompleteEvidence() throws Exception {
        Path dir=recording();Path file=dir.resolve("snapshots.jsonl");
        var rows=Files.readAllLines(file);Files.writeString(file,rows.getFirst()+System.lineSeparator());
        replay(dir,"partial");
    }
    @Test public void absentOrUnfinishedMetadataIsNotComplete() throws Exception {
        Path dir=recording();Files.deleteIfExists(dir.resolve("recording.json"));replay(dir,"unknown");
        Path unfinished=directory("coverage-unfinished-");
        try(var store=new SnapshotStore();var history=new HistoryStore(null,store,unfinished,16)) {
            store.publish(MockSnapshotFactory.create());store.emit("retained","info","retained");history.flush();
            replay(unfinished,"partial");
        }
    }
    @Test public void controlledInteriorQueueLossSurvivesRecorderShutdownAndReplay() throws Exception {
        Path dir=directory("coverage-loss-");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        RecordingWriter.OutputFactory io=path->new BufferedWriter(Files.newBufferedWriter(path,StandardOpenOption.CREATE_NEW)) {
            @Override public void write(String value,int offset,int length) throws IOException {
                if(path.getFileName().toString().equals("snapshots.jsonl")&&entered.getCount()>0) {
                    entered.countDown();await(release);
                }
                super.write(value,offset,length);
            }
        };
        try(var store=new SnapshotStore();var history=new HistoryStore(null,store,dir,1,io)) {
            store.publish(MockSnapshotFactory.create());assertTrue(entered.await(3,TimeUnit.SECONDS));
            store.emit("retained","info","retained");store.emit("death","critical","dropped");
            assertEquals(1,history.metrics().get("dropped").getAsLong());release.countDown();history.flush();
            store.publish(MockSnapshotFactory.create());history.flush();
        } finally {release.countDown();}
        assertEquals(1,json(dir.resolve("recording.json")).get("dropped").getAsLong());
        var state=replay(dir,"partial");assertEquals(4,state.getAsJsonObject("source").get("recorded_seq").getAsLong());
        replayInFreshProcess(dir);
    }
    @Test public void actualWriteFlushAndCloseFailuresCannotComplete() throws Exception {
        for(String operation:List.of("write","flush","close")) {
            Path dir=directory("coverage-failure-"+operation+"-");var armed=new AtomicBoolean();
            RecordingWriter.OutputFactory io=path->new BufferedWriter(Files.newBufferedWriter(path,StandardOpenOption.CREATE_NEW)) {
                void fault(String op) throws IOException {if(operation.equals(op)&&armed.compareAndSet(true,false))throw new IOException("injected "+op);}
                @Override public void write(String value,int offset,int length) throws IOException {fault("write");super.write(value,offset,length);}
                @Override public void flush() throws IOException {fault("flush");super.flush();}
                @Override public void close() throws IOException {try {super.close();}finally {fault("close");}}
            };
            try(var store=new SnapshotStore();var history=new HistoryStore(null,store,dir,16,io)) {
                store.publish(MockSnapshotFactory.create());store.emit("retained","info","retained");history.flush();armed.set(true);
                if(operation.equals("write"))store.emit("failed","info","failed");
                if(!operation.equals("close"))history.flush();
            }
            var manifest=json(dir.resolve("recording.json"));
            assertEquals(operation,"incomplete",manifest.get("state").getAsString());assertTrue(manifest.get("errors").getAsLong()>0);
            assertEquals(operation.equals("write")?3:2,manifest.get("accepted_rows").getAsLong());
            assertEquals(2,manifest.get("snapshots").getAsLong()+manifest.get("events").getAsLong());
            replay(dir,"partial");
        }
    }
    @Test public void metadataWriteFailureDegradesWithoutStoppingProducer() throws Exception {
        Path dir=directory("coverage-metadata-failure-");
        try(var store=new SnapshotStore();var history=new HistoryStore(null,store,dir,16)) {
            store.publish(MockSnapshotFactory.create());history.flush();
            Files.delete(dir.resolve("recording.json"));Files.createDirectory(dir.resolve("recording.json"));
            store.emit("retained","info","retained");history.flush();
            assertFalse(store.warnings().isEmpty());assertEquals(1,store.totalEvents());
        }
        replay(dir,"unknown");
    }
    @Test public void corruptMetadataIsUnknownAndPreexistingMetadataIsNeverOverwritten() throws Exception {
        Path dir=recording();Files.writeString(dir.resolve("recording.json"),"{broken");replay(dir,"unknown");
        Path occupied=directory("coverage-occupied-");Files.writeString(occupied.resolve("recording.json"),"owned earlier");
        try(var store=new SnapshotStore();var history=new HistoryStore(null,store,occupied,16)) {
            store.publish(MockSnapshotFactory.create());history.flush();assertFalse(store.warnings().isEmpty());
        }
        assertEquals("owned earlier",Files.readString(occupied.resolve("recording.json")));
        assertFalse(Files.exists(occupied.resolve("snapshots.jsonl")));
    }
    @Test public void timedOutShutdownNeverCompletesAfterDelayedCloseReturns() throws Exception {
        Path dir=directory("coverage-timeout-");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        RecordingWriter.OutputFactory io=path->new BufferedWriter(Files.newBufferedWriter(path,StandardOpenOption.CREATE_NEW)) {
            @Override public void close() throws IOException {
                if(path.getFileName().toString().equals("snapshots.jsonl")) {entered.countDown();try {if(!release.await(8,TimeUnit.SECONDS))throw new IOException("close latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
                super.close();
            }
        };
        try(var store=new SnapshotStore()) {
            var history=new HistoryStore(null,store,dir,16,io);store.publish(MockSnapshotFactory.create());history.flush();
            var closer=CompletableFuture.runAsync(history::close);
            assertTrue(entered.await(3,TimeUnit.SECONDS));closer.get(6,TimeUnit.SECONDS);release.countDown();
            long deadline=System.nanoTime()+3_000_000_000L;
            while(json(dir.resolve("recording.json")).get("errors").getAsLong()==0&&System.nanoTime()<deadline)Thread.sleep(10);
            assertEquals("incomplete",json(dir.resolve("recording.json")).get("state").getAsString());
            assertTrue(store.warnings().contains("history_close_timeout"));
        } finally {release.countDown();}
    }
    @Test public void completionEligibilityIsDecidedOnceAtDataClose() throws Exception {
        Path dir=directory("coverage-once-");var calls=new AtomicInteger();
        try(var store=new SnapshotStore()) {
            store.publish(MockSnapshotFactory.create());
            var recording=new RecordingWriter(dir,store.instanceId(),RecordingWriter.FILES);
            recording.append(new Commit(1,0,store.current(),null));
            recording.close(()->calls.incrementAndGet()==1,1);
        }
        assertEquals("eligibility must not be re-read during metadata publication",1,calls.get());
        assertEquals("complete",json(dir.resolve("recording.json")).get("state").getAsString());
    }
    @Test public void delayedFinalMetadataDoesNotUndoAlreadyCompletedData() throws Exception {
        metadataBoundary(false);
    }
    @Test public void failedFinalMetadataLeavesIncompleteEvidenceAndAllData() throws Exception {
        metadataBoundary(true);
    }
    private static void metadataBoundary(boolean fail) throws Exception {
        Path dir=directory("coverage-publication-");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var published=new CountDownLatch(1);var streamsClosed=new AtomicInteger();
        RecordingWriter.OutputFactory output=path->new BufferedWriter(Files.newBufferedWriter(path,StandardOpenOption.CREATE_NEW)) {
            @Override public void close() throws IOException {super.close();streamsClosed.incrementAndGet();}
        };
        RecordingWriter.ManifestPublisher metadata=(temporary,target)-> {
            JsonObject evidence;
            try {evidence=json(temporary);}catch(Exception e){throw new IOException(e);}
            if(evidence.get("state").getAsString().equals("complete")) {
                assertEquals(2,streamsClosed.get());entered.countDown();
                try {if(!release.await(8,TimeUnit.SECONDS))throw new IOException("metadata latch timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
                if(fail)throw new IOException("final manifest publication failed");
                Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);published.countDown();
            } else Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        };
        try(var store=new SnapshotStore()) {
            var history=new HistoryStore(null,store,dir,16,output,metadata);
            store.publish(MockSnapshotFactory.create());store.emit("retained","info","retained");store.publish(MockSnapshotFactory.create());
            var closer=CompletableFuture.runAsync(history::close);
            assertTrue(entered.await(3,TimeUnit.SECONDS));closer.get(6,TimeUnit.SECONDS);
            assertTrue(store.warnings().contains("history_close_timeout"));
            // All accepted rows and both closed streams precede this delayed metadata boundary.
            assertEquals(2,Files.readAllLines(dir.resolve("snapshots.jsonl")).size());assertEquals(1,Files.readAllLines(dir.resolve("events.jsonl")).size());
            release.countDown();
            if(fail) {
                long deadline=System.nanoTime()+3_000_000_000L;
                while(!store.warnings().contains("recording_close_failed")&&System.nanoTime()<deadline)Thread.sleep(5);
                assertTrue(store.warnings().contains("recording_close_failed"));assertEquals("incomplete",json(dir.resolve("recording.json")).get("state").getAsString());
                replay(dir,"partial");
            } else {
                assertTrue(published.await(3,TimeUnit.SECONDS));var evidence=json(dir.resolve("recording.json"));
                assertEquals("complete",evidence.get("state").getAsString());assertTrue(evidence.get("data_drained").getAsBoolean());
                assertEquals(3,evidence.get("accepted_rows").getAsLong());
                assertEquals("data_closed_before_deadline_v1",evidence.get("completion_policy").getAsString());
                replay(dir,"complete");
            }
        } finally {release.countDown();}
    }
    private static void await(CountDownLatch latch) throws IOException {
        try {if(!latch.await(3,TimeUnit.SECONDS))throw new IOException("write latch timeout");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
    }
    private static void replayInFreshProcess(Path dir) throws Exception {
        Path configDir=directory("coverage-process-");Files.writeString(configDir.resolve("info2ai.json"),"{\"http\":{\"port\":0}}");
        var config=Info2AiConfig.load(configDir);
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
            "-Dinfo2ai.configDir="+configDir.toAbsolutePath(),"-Dinfo2ai.replay="+dir.toAbsolutePath(),
            "-cp",System.getProperty("info2ai.testRuntimeClasspath"),"com.example.info2ai.http.ReplayMain")
            .redirectError(configDir.resolve("stderr.log").toFile()).start();
        try {
            String line=CompletableFuture.supplyAsync(()-> {
                try {return process.inputReader().readLine();}catch(IOException e){throw new RuntimeException(e);}
            }).get(10,TimeUnit.SECONDS);
            assertTrue(line,line.startsWith("info2ai REPLAY: "));String base=line.substring("info2ai REPLAY: ".length());
            var client=HttpClient.newHttpClient();JsonObject health=null;
            for(int i=0;i<100;i++) {
                health=processGet(client,base,config,"/health");
                if(health.get("source_status").getAsString().equals("finished_partial"))break;Thread.sleep(20);
            }
            assertEquals("finished_partial",health.get("source_status").getAsString());assertTrue(health.get("degraded").getAsBoolean());
            assertTrue(health.getAsJsonArray("warnings").toString().contains("replay_recording_producer_loss"));
            assertEquals("partial",processGet(client,base,config,"/state").getAsJsonObject("source").get("coverage").getAsString());
            var events=processGet(client,base,config,"/events/history").getAsJsonArray("events");assertEquals(1,events.size());
            assertEquals("retained",events.get(0).getAsJsonObject().get("kind").getAsString());
            assertEquals("partial",events.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("replay").get("coverage").getAsString());
        } finally {process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS))process.destroyForcibly();}
    }
    private static JsonObject processGet(HttpClient client,String base,Info2AiConfig config,String path) throws Exception {
        var response=client.send(HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer "+config.token()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    static JsonObject replay(Path dir,String coverage) throws Exception {
        Path configDir=directory("coverage-http-");Files.writeString(configDir.resolve("info2ai.json"),"{\"http\":{\"port\":0}}");
        var config=Info2AiConfig.load(configDir);
        try(var store=new SnapshotStore();var replay=new ReplaySnapshotSource(dir,store);var host=HttpServerHost.start(config,store)) {
            replay.start();long deadline=System.nanoTime()+5_000_000_000L;
            while(!replay.finished()&&System.nanoTime()<deadline)Thread.sleep(5);
            assertTrue(replay.finished());
            var state=get(host,config,"/state");var health=get(host,config,"/health");
            assertEquals(coverage,state.getAsJsonObject("source").get("coverage").getAsString());
            assertEquals(!coverage.equals("complete"),health.get("degraded").getAsBoolean());
            assertEquals(coverage.equals("complete")?"finished":"finished_partial",health.get("source_status").getAsString());
            var events=get(host,config,"/events/history").getAsJsonArray("events");
            for(var event:events)assertEquals(coverage,event.getAsJsonObject().getAsJsonObject("data").getAsJsonObject("replay").get("coverage").getAsString());
            if(!coverage.equals("complete"))assertTrue(health.getAsJsonArray("warnings").toString().contains("replay_recording_"));
            return state;
        }
    }
    private static JsonObject get(HttpServerHost host,Info2AiConfig config,String path) throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+host.actualPort()+path))
            .header("Authorization","Bearer "+config.token()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(response.body(),200,response.statusCode());return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}

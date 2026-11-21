package com.example.info2ai.history;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.model.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

/** Read-only two-way merge. All validation/read/scheduling runs on a standalone daemon, never Minecraft. */
public final class ReplaySnapshotSource implements AutoCloseable {
    private final Path path;
    private final SnapshotStore store;
    private final ScheduledExecutorService executor;
    private Timeline timeline;
    private Coverage coverage=new Coverage("unknown","metadata_unchecked",0,0);
    private long started;
    private volatile boolean finished;
    private boolean launched;
    public ReplaySnapshotSource(Path path,SnapshotStore store) {
        this.path=path;this.store=store;store.replayMode();
        executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"info2ai-replay");t.setDaemon(true);return t;});
    }
    public synchronized void start() {
        if(launched)return;launched=true;
        executor.execute(()-> {
            try {
                // Validate the entire merged order before exposing any partial recording as available.
                try(var validation=new Timeline(path)) {
                    while(validation.next()!=null){}
                    coverage=Coverage.read(path,validation);
                }
                if(!coverage.complete())store.warning("replay_recording_"+coverage.reason());
                timeline=new Timeline(path);started=System.nanoTime();store.sourceStatus(coverage.complete()?"playing":"playing_partial");
                executor.scheduleWithFixedDelay(this::advance,0,5,TimeUnit.MILLISECONDS);
            } catch(IOException|RuntimeException failure) {fail(failure);}
        });
    }
    public boolean finished() {return finished;}
    private void advance() {
        if(finished)return;
        try {
            for(int i=0;i<256;i++) {
                Row next=timeline.peek();
                if(next==null) {finished=true;store.sourceStatus(coverage.complete()?"finished":"finished_partial");timeline.close();return;}
                if(next.elapsed>System.nanoTime()-started)return;
                next=timeline.next();
                if(next.snapshot!=null)store.publishRecorded(next.snapshot,next.provenance(coverage));
                else store.emitRecorded(next.event,next.provenance(coverage));
            }
        } catch(IOException|RuntimeException failure) {fail(failure);}
    }
    private void fail(Exception failure) {
        finished=true;store.replayUnavailable();
        LoggerFactory.getLogger("info2ai.replay").error("Replay unavailable ({})",failure.getClass().getSimpleName());
        if(timeline!=null)try {timeline.close();}catch(IOException close){store.warning("replay_close_failed");}
    }
    @Override public void close() {
        finished=true;
        executor.execute(()->{if(timeline!=null)try {timeline.close();}catch(IOException e){store.warning("replay_close_failed");}});
        executor.shutdown();
        try {if(!executor.awaitTermination(2,TimeUnit.SECONDS))executor.shutdownNow();}
        catch(InterruptedException e){Thread.currentThread().interrupt();store.warning("replay_close_interrupted");}
    }
    private record Row(long order,long elapsed,String instance,String world,Snapshot snapshot,GameEvent event) {
        Facts provenance(Coverage coverage) {
            var o=new JsonObject();o.addProperty("mode","replay");o.addProperty("recorded_seq",snapshot==null?event.seq():snapshot.seq());
            o.addProperty("recorded_instance_id",instance);o.addProperty("world_id",world);o.addProperty("order",order);o.addProperty("elapsed_ns",elapsed);
            o.addProperty("confidence","recorded");o.addProperty("coverage",coverage.value());o.addProperty("coverage_reason",coverage.reason());
            o.addProperty("recorded_dropped",coverage.dropped());o.addProperty("recorded_errors",coverage.errors());return Facts.fromJson(o);
        }
    }
    private record Coverage(String value,String reason,long dropped,long errors) {
        boolean complete() {return value.equals("complete");}
        static Coverage read(Path directory,Timeline actual) {
            Path metadata=directory.resolve("recording.json");
            try {
                if(!Files.exists(metadata))return new Coverage("unknown","metadata_missing",0,0);
                if(Files.size(metadata)>65536)throw new IOException("recording metadata too large");
                var o=JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
                if(o.get("schema").getAsInt()!=1)throw new IOException("recording metadata schema");
                String state=o.get("state").getAsString();
                if(!state.equals("complete")&&!state.equals("incomplete"))throw new IOException("recording metadata state");
                long dropped=nonnegative(o,"dropped"),errors=nonnegative(o,"errors");
                if(dropped>0||errors>0)return new Coverage("partial","producer_loss",dropped,errors);
                if(!state.equals("complete"))return new Coverage("partial","unfinished",dropped,errors);
                if(!"data_closed_before_deadline_v1".equals(o.get("completion_policy").getAsString()))
                    throw new IOException("recording completion policy");
                boolean matches=o.get("data_drained").getAsBoolean()
                    &&nonnegative(o,"accepted_rows")==actual.snapshotCount+actual.eventCount
                    &&o.get("source_instance_id").getAsString().equals(actual.instance)
                    &&nonnegative(o,"snapshots")==actual.snapshotCount&&nonnegative(o,"events")==actual.eventCount
                    &&nonnegative(o,"first_order")==actual.firstOrder&&nonnegative(o,"last_order")==actual.lastOrder
                    &&nonnegative(o,"snapshots_bytes")==Files.size(directory.resolve("snapshots.jsonl"))
                    &&nonnegative(o,"events_bytes")==Files.size(directory.resolve("events.jsonl"));
                if(!matches)return new Coverage("partial","content_mismatch",dropped,errors);
                if(actual.gap)return new Coverage("partial","order_gap",dropped,errors);
                return new Coverage("complete","validated",0,0);
            } catch(IOException|RuntimeException bad) {
                LoggerFactory.getLogger("info2ai.replay").warn("Recording completion metadata unavailable ({})",bad.getClass().getSimpleName());
                return new Coverage("unknown","metadata_invalid",0,0);
            }
        }
        private static long nonnegative(JsonObject o,String key) throws IOException {
            long value=o.get(key).getAsLong();if(value<0)throw new IOException("negative recording metadata");return value;
        }
    }
    private static final class Timeline implements AutoCloseable {
        private final BufferedReader snapshots,events;
        private Row snapshot,event;
        private long lastOrder=-1,lastElapsed=-1,firstOrder,snapshotCount,eventCount;
        private boolean gap;
        private String instance;
        Timeline(Path path) throws IOException {
            snapshots=Files.newBufferedReader(path.resolve("snapshots.jsonl"));
            try {events=Files.newBufferedReader(path.resolve("events.jsonl"));}
            catch(IOException failure){snapshots.close();throw failure;}
            try {snapshot=read(snapshots,true);event=read(events,false);if(snapshot==null)throw new IOException("recording has no snapshots");}
            catch(IOException|RuntimeException failure){close();throw failure;}
        }
        Row peek() {return event==null?snapshot:snapshot==null?event:snapshot.order<event.order?snapshot:event;}
        Row next() throws IOException {
            Row row=peek();if(row==null)return null;
            if(row.order<=lastOrder||row.elapsed<lastElapsed)throw new IOException("nonmonotonic recording");
            if(instance!=null&&!instance.equals(row.instance))throw new IOException("mixed recording instances");
            if(firstOrder==0)firstOrder=row.order;
            if(row.order!=(lastOrder<0?1:lastOrder+1))gap=true;
            if(row.snapshot!=null)snapshotCount++;else eventCount++;
            instance=row.instance;lastOrder=row.order;lastElapsed=row.elapsed;
            if(row==snapshot)snapshot=read(snapshots,true);else event=read(events,false);
            return row;
        }
        private static Row read(Reader reader,boolean isSnapshot) throws IOException {
            String line=JsonLines.read(reader);if(line==null)return null;
            try {
                var o=JsonParser.parseString(line).getAsJsonObject();
                if(o.get("schema").getAsInt()!=1||!o.get("type").getAsString().equals(isSnapshot?"snapshot":"event"))throw new IOException("recording schema");
                long order=o.get("order").getAsLong(),elapsed=o.get("elapsed_ns").getAsLong();
                if(order<1||elapsed<0)throw new IOException("invalid recording order/time");
                String instance=o.get("source_instance_id").getAsString();
                String world=o.get("world_id").isJsonNull()?null:o.get("world_id").getAsString();
                var payload=o.getAsJsonObject("payload");Snapshot s=isSnapshot?Snapshot.fromJson(payload):null;GameEvent e=isSnapshot?null:GameEvent.fromJson(payload);
                if(s!=null&&(s.seq()!=order||s.capturedAt()<0||s.detail()!=Detail.NORMAL))throw new IOException("invalid snapshot metadata");
                if(e!=null&&(e.seq()==null||e.seq()!=order||e.t()==null||e.tick()==null||e.kind()==null||e.t()<0))throw new IOException("invalid event metadata");
                String payloadWorld=s!=null?(s.session()==null?null:s.session().worldId()):new Commit(order,elapsed,null,e).worldId();
                if(!java.util.Objects.equals(world,payloadWorld))throw new IOException("world identity mismatch");
                return new Row(order,elapsed,instance,world,s,e);
            } catch(RuntimeException malformed){throw new IOException("invalid recording row",malformed);}
        }
        @Override public void close() throws IOException {try{snapshots.close();}finally{events.close();}}
    }
}

package com.example.info2ai.history;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.model.*;
import com.example.info2ai.util.JsonUtil;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;

/** Single disk owner. Producers offer immutable commits; restore/serialization/flush never run on tick. */
public final class HistoryStore implements AutoCloseable {
    private final Path root;
    private final SnapshotStore store;
    private final BlockingQueue<Commit> queue;
    private final BlockingQueue<Commit> lifecycle=new ArrayBlockingQueue<>(64);
    private long lastOfferedEpoch=Long.MIN_VALUE;
    private final ScheduledExecutorService writer;
    private final AtomicLong dropped=new AtomicLong(),failures=new AtomicLong(),accepted=new AtomicLong();
    private final Set<String> warnings=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private volatile View view;
    private Session session;
    private final Path recordingPath;
    private RecordingWriter recording;
    private final RecordingWriter.OutputFactory recordingOutput;
    private final RecordingWriter.ManifestPublisher recordingMetadata;
    private final java.util.concurrent.atomic.AtomicBoolean closeAborted=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile long closeDeadline=Long.MAX_VALUE;
    private boolean recordingAttempted,recordingFailed;
    public boolean persistent() { return root!=null; }
    private long lastFlush=System.nanoTime(),lastLog;
    private record View(String world,long epoch,HistoryInfo history,Set<String> biomes,Set<String> structures) { }
    public boolean structureObserved(String world,long epoch,String key) {
        View current=view;return current!=null&&current.epoch()==epoch&&Objects.equals(world,current.world())&&current.structures().contains(key);
    }
    private static String structureKey(Facts facts) {
        var data=facts.toJson();String stable=JsonUtil.string(data,"key");return stable==null?data.toString():stable;
    }
    public record BiomeMemory(Boolean observed,String scope,boolean absoluteFirstKnown) { }
    public BiomeMemory biomeMemory(String world,String biome) {
        View current=view;
        if(current==null||!Objects.equals(current.world(),world)||current.epoch()!=store.worldEpoch()||biome==null)
            return new BiomeMemory(null,"observed_since_install",false);
        // Absence from observations can never establish absence before installation.
        return new BiomeMemory(current.biomes().contains(biome)?Boolean.TRUE:null,"observed_since_install",false);
    }
    public HistoryStore(Path root,SnapshotStore store,Path recording,int capacity) {
        this(root,store,recording,capacity,RecordingWriter.FILES);
    }
    HistoryStore(Path root,SnapshotStore store,Path recording,int capacity,RecordingWriter.OutputFactory recordingOutput) {
        this(root,store,recording,capacity,recordingOutput,RecordingWriter.ATOMIC);
    }
    HistoryStore(Path root,SnapshotStore store,Path recording,int capacity,RecordingWriter.OutputFactory recordingOutput,RecordingWriter.ManifestPublisher recordingMetadata) {
        this.recordingOutput=recordingOutput;this.recordingMetadata=recordingMetadata;
        this.root=root;this.store=store;queue=new ArrayBlockingQueue<>(capacity);
        recordingPath=recording;
        writer=Executors.newSingleThreadScheduledExecutor(task->{var t=new Thread(task,"info2ai-history-writer");t.setDaemon(true);return t;});
        store.history(this);
        writer.scheduleWithFixedDelay(this::drainSafely,20,20,TimeUnit.MILLISECONDS);
    }
    public void offer(Commit commit) {
        if(closed)return;
        boolean boundary=commit.epoch()!=lastOfferedEpoch||commit.event()!=null&&"world_unloaded".equals(commit.event().kind());
        lastOfferedEpoch=commit.epoch();
        if(!(boundary?lifecycle:queue).offer(commit)) {
            dropped.incrementAndGet();warnings.add(boundary?"history_lifecycle_overflow":"history_queue_overflow");
        } else accepted.incrementAndGet();
    }
    public List<String> warnings() { return List.copyOf(warnings); }
    public JsonObject metrics() {
        var o=new JsonObject();o.addProperty("queued",queue.size()+lifecycle.size());o.addProperty("dropped",dropped.get());o.addProperty("failures",failures.get());return o;
    }
    public HistoryInfo snapshot(String world) {return snapshot(world,store.worldEpoch());}
    public HistoryInfo snapshot(String world,long epoch) {
        View current=view;
        if(current!=null&&Objects.equals(current.world(),world)&&current.epoch()==epoch)return current.history();
        return new HistoryInfo(null,null,null,null,List.of(),null,null,null,null,
            com.example.info2ai.events.EventData.of("status","loading","source","info2ai_observed_history","confidence","low","pre_observation_known",false));
    }
    private void failure(String code,Throwable failure) {
        failures.incrementAndGet();warnings.add(code);
        long now=System.nanoTime();
        if(now-lastLog>=1_000_000_000L) {
            lastLog=now;LoggerFactory.getLogger("info2ai.history").error("History I/O failed: {} ({})",code,failure.getClass().getSimpleName());
        }
    }
    private void drainSafely() {
        try {
            for(int n=0;n<1024;n++) {
                Commit data=queue.peek(),boundary=lifecycle.peek();
                // Producers enqueue in global order under the store lock. Recheck an empty lane
                // after observing the other: it may have gained an EARLIER commit between peeks.
                if(data==null&&boundary!=null)data=queue.peek();
                if(boundary==null&&data!=null)boundary=lifecycle.peek();
                if(data==null&&boundary==null)break;
                Commit c=boundary==null?queue.poll():data==null||boundary.order()<data.order()?lifecycle.poll():queue.poll();if(c==null)break;
                record(c);
                try { accept(c); } catch(IOException|RuntimeException e) {
                    failure("history_write_failed",e);if(session!=null)session.partial=true;
                    if(c.worldId()!=null)view=new View(c.worldId(),c.epoch(),new HistoryInfo(null,null,null,null,List.of(),null,null,null,null,
                        com.example.info2ai.events.EventData.of("status","unavailable","source","info2ai_observed_history","confidence","low","pre_observation_known",false)),Set.of(),Set.of());
                }
            }
            if(recording!=null)recording.loss(dropped.get());
            if(System.nanoTime()-lastFlush>=30_000_000_000L)flushOwned();
        } catch(IOException|RuntimeException e) { failure("history_flush_failed",e); }
    }
    private void record(Commit c) {
        if(recordingPath==null||recordingFailed)return;
        try {
            if(!recordingAttempted) {recordingAttempted=true;recording=new RecordingWriter(recordingPath,store.instanceId(),recordingOutput,recordingMetadata);}
            recording.append(c);
        } catch(IOException|RuntimeException failure) {recordingFailed=true;failure("recording_write_failed",failure);}
    }
    private void closeRecording() {
        if(recording==null)return;
        try {recording.loss(dropped.get());}
        catch(IOException e){recordingFailed=true;failure("recording_metadata_failed",e);}
        // A metadata error must not skip closing the data streams.
        try {recording.close(()->!recordingFailed&&!closeAborted.get()&&System.nanoTime()<closeDeadline&&queue.isEmpty()&&lifecycle.isEmpty(),accepted.get());}
        catch(IOException e){failure("recording_close_failed",e);}
    }
    private void accept(Commit c) throws IOException {
        if(root==null)return;
        String id=c.worldId();
        if(id==null)return;
        if(!id.matches("[a-f0-9]{64}")) { warnings.add("history_world_id_invalid");return; }
        if(session==null||!session.id.equals(id)||session.epoch!=c.epoch()) {
            if(c.snapshot()==null)return; // never reopen a session for a delayed unload alone
            finishSession();Session next=new Session(id,c.epoch());next.open(c);session=next;
        }
        if(c.snapshot()!=null)session.snapshot(c.snapshot());
        else session.event(c.event());
        view=new View(id,c.epoch(),session.view(),Set.copyOf(session.biomes),Set.copyOf(session.structures.keySet()));
        if(c.event()!=null&&"world_unloaded".equals(c.event().kind()))finishSession();
    }
    private void finishSession() throws IOException {
        if(session!=null) {
            Session previous=session;session=null;
            try { previous.flush(); } finally { previous.output.close(); }
        }
    }
    private void flushOwned() throws IOException { if(session!=null)session.flush();if(recording!=null&&!recordingFailed)recording.flush();lastFlush=System.nanoTime(); }
    /** Only lifecycle/test/administrative callers; never call from capture or render threads. */
    public void flush() throws Exception {
        writer.submit(()->{while(!queue.isEmpty()||!lifecycle.isEmpty())drainSafely();try {flushOwned();}catch(IOException e){failure("history_flush_failed",e);}}).get(5,TimeUnit.SECONDS);
    }
    @Override public void close() {
        // Offers originate under SnapshotStore's publication lock. Fence them before
        // capturing/draining the accepted count; later publications cannot enter this recorder.
        synchronized(store) {if(closed)return;closed=true;}
        closeDeadline=System.nanoTime()+5_000_000_000L;
        try {
            writer.submit(()->{while(!queue.isEmpty()||!lifecycle.isEmpty())drainSafely();try {finishSession();}catch(IOException e){failure("history_close_failed",e);}finally {closeRecording();}}).get(5,TimeUnit.SECONDS);
        } catch(InterruptedException e) {closeAborted.set(true); Thread.currentThread().interrupt();failure("history_close_interrupted",e); }
        catch(ExecutionException|TimeoutException e) {closeAborted.set(true); failure("history_close_timeout",e); }
        finally {writer.shutdown();}
    }
    private final class Session {
        final String id;
        final long epoch;
        final Path directory;
        BufferedWriter output;
        String logName;
        long sessions,deaths,lastSeen,lastSeq,lastPositionTime=Long.MIN_VALUE;
        int sessionEvents,sessionDeaths;
        boolean partial;
        JsonObject lastDeath;
        JsonArray lastPos;
        String activity;
        Facts previous;
        final LinkedHashSet<String> biomes=new LinkedHashSet<>();
        final LinkedHashMap<String,Facts> structures=new LinkedHashMap<>();
        final ArrayDeque<List<Double>> positions=new ArrayDeque<>();
        final ArrayDeque<Facts> dimensions=new ArrayDeque<>();
        Session(String id,long epoch) {this.id=id;this.epoch=epoch;directory=root.resolve(id);}
        void open(Commit first) throws IOException {
            Files.createDirectories(directory);restore();
            if(sessions>0||partial) {
                var p=new JsonObject();p.addProperty("last_seen_s_ago",lastSeen==0?null:Math.max(0,(System.currentTimeMillis()-lastSeen)/1000));
                p.add("last_pos",lastPos==null?JsonNull.INSTANCE:lastPos.deepCopy());p.addProperty("last_activity",activity);
                p.addProperty("last_seen_at",lastSeen==0?null:lastSeen);p.addProperty("sessions_count",sessions+1);p.addProperty("source","previous_recorded_session");p.addProperty("confidence",partial?"low":"medium");previous=Facts.fromJson(p);
            }
            long timestamp=System.currentTimeMillis();
            while(Files.exists(directory.resolve(timestamp+".jsonl")))timestamp++;
            logName=timestamp+".jsonl";
            output=Files.newBufferedWriter(directory.resolve(logName),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            sessions++;lastSeq=0;sessionEvents=0;sessionDeaths=0;
            var row=base("session",timestamp,0);
            row.addProperty("world_id",id);row.addProperty("schema",1);write(row);
        }
        void restore() throws IOException {
            Path checkpoint=directory.resolve("checkpoint.json");String through=null;long sequence=0;
            if(Files.exists(checkpoint)) {
                try {
                    if(Files.size(checkpoint)>1_048_576)throw new IOException("checkpoint too large");
                    JsonObject o=JsonParser.parseString(Files.readString(checkpoint)).getAsJsonObject();
                    if(o.get("schema").getAsInt()!=1||!id.equals(o.get("world_id").getAsString()))throw new IOException("checkpoint schema");
                    sessions=o.get("sessions").getAsLong();deaths=o.get("deaths").getAsLong();
                    if(sessions<0||deaths<0)throw new IOException("negative counters");
                    lastSeen=o.get("last_seen").getAsLong();lastDeath=JsonUtil.object(o,"last_death");
                    lastPos=o.has("last_pos")&&o.get("last_pos").isJsonArray()?o.getAsJsonArray("last_pos"):null;
                    activity=JsonUtil.string(o,"activity");partial=o.get("partial").getAsBoolean();
                    for(var b:o.getAsJsonArray("biomes"))biomes.add(b.getAsString());
                    for(var st:o.getAsJsonArray("structures")) {var fact=Facts.fromJson(st.getAsJsonObject());structures.put(structureKey(fact),fact);}
                    through=o.get("last_log").getAsString();sequence=o.get("last_seq").getAsLong();
                } catch(IOException|RuntimeException failure) {
                    sessions=0;deaths=0;lastSeen=0;lastDeath=null;lastPos=null;activity=null;biomes.clear();structures.clear();
                    partial=true;failure("history_checkpoint_corrupt",failure);through=null;
                    Files.move(checkpoint,directory.resolve("checkpoint.corrupt-"+UUID.randomUUID()+".json"));
                }
            }
            List<Path> logs;
            try(var files=Files.list(directory)) {logs=files.filter(p->p.getFileName().toString().matches("[0-9]+\\.jsonl")).sorted().toList();}
            for(Path log:logs) {
                String name=log.getFileName().toString();
                if(through!=null&&name.compareTo(through)<0)continue;
                boolean same=name.equals(through);
                try(var reader=Files.newBufferedReader(log)) {
                    String line;long rowSequence=-1;
                    while((line=JsonLines.read(reader))!=null) {
                        try {
                            var row=JsonParser.parseString(line).getAsJsonObject();
                            if(!id.equals(row.get("world_id").getAsString())||row.get("schema").getAsInt()!=1)throw new IllegalArgumentException("row identity/schema");
                            long seq=row.get("seq").getAsLong();
                            if(seq<0||seq<=rowSequence||row.get("t").getAsLong()<0)throw new IllegalArgumentException("history row order/time");
                            rowSequence=seq;
                            if("event".equals(row.get("type").getAsString())) {
                                var event=GameEvent.fromJson(row.getAsJsonObject("event"));
                                if(event.seq()==null||event.seq()!=seq||event.t()==null||event.t()!=row.get("t").getAsLong()||event.kind()==null)
                                    throw new IllegalArgumentException("history event metadata");
                            }
                            if(same&&seq<=sequence)continue;
                            apply(row,true);
                        } catch(RuntimeException bad) {partial=true;failure("history_log_corrupt",bad);}
                    }
                }
            }
            String expectedLog=through;
            if(through!=null&&logs.stream().noneMatch(p->p.getFileName().toString().equals(expectedLog))) {
                partial=true;warnings.add("history_log_missing");
            }
        }
        void snapshot(Snapshot s) throws IOException {
            lastSeen=s.capturedAt();
            if(s.player()!=null) {
                lastPos=new JsonArray();if(s.player().pos()!=null)s.player().pos().forEach(lastPos::add);
                activity=s.player().activity()==null?null:JsonUtil.string(s.player().activity().toJson(),"kind");
            }
            if(s.surroundings()!=null&&s.surroundings().biome()!=null)rememberBiome(s.surroundings().biome());
            if(lastPositionTime==Long.MIN_VALUE||s.capturedAt()-lastPositionTime>=10000) {
                lastPositionTime=s.capturedAt();
                var row=base("position",s.capturedAt(),s.seq());row.add("pos",lastPos);row.addProperty("activity",activity);
                row.addProperty("biome",s.surroundings()==null?null:s.surroundings().biome());row.addProperty("tick",s.tick());
                apply(row,false);write(row);
            }
        }
        void event(GameEvent event) throws IOException {
            var row=base("event",event.t(),event.seq());row.add("event",event.toJson(Detail.FULL));
            apply(row,false);write(row);
        }
        JsonObject base(String type,long t,long seq) {var o=new JsonObject();o.addProperty("type",type);o.addProperty("t",t);o.addProperty("seq",seq);o.addProperty("schema",1);o.addProperty("world_id",id);provenance(o);return o;}
        void apply(JsonObject row,boolean restoring) {
            long t=row.get("t").getAsLong();lastSeen=Math.max(lastSeen,t);
            switch(row.get("type").getAsString()) {
                case "session" -> {if(restoring)sessions++;}
                case "position" -> {
                    if(row.get("pos")!=null&&row.get("pos").isJsonArray()) {
                        lastPos=row.getAsJsonArray("pos");
                        if(!restoring) {positions.addFirst(JsonUtil.doubles(row,"pos"));while(positions.size()>30)positions.removeLast();}
                    }
                    activity=JsonUtil.string(row,"activity");String biome=JsonUtil.string(row,"biome");if(biome!=null)rememberBiome(biome);
                }
                case "event" -> {
                    var event=GameEvent.fromJson(row.getAsJsonObject("event"));
                    if(!restoring)sessionEvents++;
                    if("death".equals(event.kind())) {
                        deaths++;if(!restoring)sessionDeaths++;
                        lastDeath=event.data()==null?new JsonObject():event.data().toJson();lastDeath.addProperty("t",t);
                        if(event.pos()!=null) {lastDeath.addProperty("x",event.pos().get(0));lastDeath.addProperty("z",event.pos().get(2));}
                    }
                    if("structure_found".equals(event.kind())&&event.data()!=null) {
                        String key=structureKey(event.data());
                        if(structures.size()<4096||structures.containsKey(key))structures.put(key,event.data());else partial=true;
                    }
                    if(!restoring&&"dimension_changed".equals(event.kind())) {
                        var d=event.data()==null?new JsonObject():event.data().toJson();d.addProperty("to",JsonUtil.string(d,"dimension"));
                        if(!d.has("day"))d.add("day",JsonNull.INSTANCE);d.addProperty("t",t);dimensions.addFirst(Facts.fromJson(d));while(dimensions.size()>100)dimensions.removeLast();
                    }
                }
                default -> throw new IllegalArgumentException("unknown history row");
            }
        }
        void rememberBiome(String biome) {if(biomes.size()<4096)biomes.add(biome);else partial=true;}
        void write(JsonObject row) throws IOException {lastSeq=Math.max(lastSeq,row.get("seq").getAsLong());output.write(row.toString());output.newLine();}
        void flush() throws IOException {
            output.flush();
            var o=new JsonObject();o.addProperty("schema",1);o.addProperty("world_id",id);o.addProperty("sessions",sessions);o.addProperty("deaths",deaths);
            o.addProperty("last_seen",lastSeen);o.add("last_pos",lastPos);o.addProperty("activity",activity);o.add("last_death",lastDeath);
            o.addProperty("partial",partial||dropped.get()>0);o.addProperty("last_log",logName);o.addProperty("last_seq",lastSeq);
            provenance(o);
            var b=new JsonArray();biomes.forEach(b::add);o.add("biomes",b);var st=new JsonArray();structures.values().forEach(f->st.add(f.toJson()));o.add("structures",st);
            Path temporary=Files.createTempFile(directory,"checkpoint-",".tmp");
            try {
                Files.writeString(temporary,o.toString());
                Files.move(temporary,directory.resolve("checkpoint.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            } finally {Files.deleteIfExists(temporary);}
        }
        void provenance(JsonObject row) {
            row.addProperty("source","info2ai_observed_history");row.addProperty("confidence",partial||dropped.get()>0?"low":"medium");
            row.addProperty("coverage","observed_since_install");row.addProperty("pre_observation_known",false);
        }
        HistoryInfo view() {
            if(previous!=null) {
                var p=previous.toJson();Long seen=JsonUtil.longNumber(p,"last_seen_at");
                p.addProperty("last_seen_s_ago",seen==null?null:Math.max(0,(System.currentTimeMillis()-seen)/1000));previous=Facts.fromJson(p);
            }
            var memory=new JsonObject();memory.addProperty("status",partial||dropped.get()>0?"partial":"ready");memory.addProperty("source","info2ai_observed_history");
            memory.addProperty("confidence",partial||dropped.get()>0?"low":"medium");memory.addProperty("pre_observation_known",false);memory.addProperty("cumulative_deaths",deaths);
            memory.addProperty("sessions_count",sessions);memory.addProperty("biome_scope","observed_since_install");
            var b=new JsonArray();biomes.forEach(b::add);memory.add("observed_biomes",b);memory.addProperty("structures_coverage","observed_events_only");
            var death=lastDeath==null?null:lastDeath.deepCopy();if(death!=null)death.addProperty("ago_s",Math.max(0,(System.currentTimeMillis()-death.get("t").getAsLong())/1000));
            List<Facts> switches=dimensions.stream().map(value->{
                var d=value.toJson();d.addProperty("ago_s",Math.max(0,(System.currentTimeMillis()-d.get("t").getAsLong())/1000));return Facts.fromJson(d);
            }).toList();
            return new HistoryInfo(sessionEvents,sessionDeaths,Facts.fromJson(death),switches,List.copyOf(positions),
                structures.isEmpty()?null:List.copyOf(structures.values()),null,null,previous,Facts.fromJson(memory));
        }
    }
}

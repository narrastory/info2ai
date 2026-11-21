package com.example.info2ai.history;

import com.example.info2ai.model.Detail;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.file.*;
import java.util.function.BooleanSupplier;

/** Owned exclusively by HistoryStore's disk worker; CREATE_NEW prevents accidental overwrite. */
final class RecordingWriter {
    @FunctionalInterface interface OutputFactory {BufferedWriter open(Path path) throws IOException;}
    @FunctionalInterface interface ManifestPublisher {void publish(Path temporary,Path target) throws IOException;}
    static final ManifestPublisher ATOMIC=(temporary,target)->Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    static final OutputFactory FILES=path->Files.newBufferedWriter(path,StandardOpenOption.CREATE_NEW);
    private BufferedWriter snapshots,events;
    private final String instance;
    private final Path path,manifest;
    private final ManifestPublisher metadata;
    private long acceptedRows;
    private boolean dataDrained;
    private long snapshotCount,eventCount,firstOrder,lastOrder,dropped,errors;
    RecordingWriter(Path path,String instance,OutputFactory output) throws IOException {
        this(path,instance,output,ATOMIC);
    }
    RecordingWriter(Path path,String instance,OutputFactory output,ManifestPublisher metadata) throws IOException {
        this.metadata=metadata;
        this.instance=instance;this.path=path;manifest=path.resolve("recording.json");
        Files.createDirectories(path);
        if(Files.exists(path.resolve("snapshots.jsonl"))||Files.exists(path.resolve("events.jsonl"))||Files.exists(manifest))throw new IOException("recording exists");
        // A permanent marker also protects interrupted initialization and concurrent recorders.
        Files.writeString(path.resolve("recording.lock"),"schema=1\n",StandardOpenOption.CREATE_NEW);
        Files.writeString(manifest,evidence(false).toString(),StandardOpenOption.CREATE_NEW);
        try {snapshots=output.open(path.resolve("snapshots.jsonl"));events=output.open(path.resolve("events.jsonl"));}
        catch(IOException failure) {
            errors++;if(snapshots!=null)try {snapshots.close();}catch(IOException close){failure.addSuppressed(close);}
            try {persist(false);}catch(IOException metadataFailure){failure.addSuppressed(metadataFailure);}throw failure;
        }
    }
    void append(Commit commit) throws IOException {
        var row=new JsonObject();row.addProperty("schema",1);row.addProperty("order",commit.order());
        row.addProperty("elapsed_ns",commit.elapsedNanos());row.addProperty("source_instance_id",instance);
        row.addProperty("world_id",commit.worldId());row.addProperty("type",commit.snapshot()==null?"event":"snapshot");
        row.add("payload",commit.snapshot()==null?commit.event().toJson(Detail.FULL):commit.snapshot().toJson(Detail.NORMAL));
        BufferedWriter out=commit.snapshot()==null?events:snapshots;
        try {out.write(row.toString());out.newLine();}
        catch(IOException failure) {failed(failure);throw failure;}
        if(firstOrder==0)firstOrder=commit.order();lastOrder=commit.order();
        if(commit.snapshot()==null)eventCount++;else snapshotCount++;
    }
    void loss(long count) throws IOException {
        if(count>dropped) {dropped=count;persist(false);}
    }
    void flush() throws IOException {
        try {snapshots.flush();events.flush();}
        catch(IOException failure) {failed(failure);throw failure;}
        persist(false);
    }
    private void failed(IOException failure) {
        errors++;
        try {persist(false);}catch(IOException metadataFailure){failure.addSuppressed(metadataFailure);}
    }
    void close(BooleanSupplier orderly,long acceptedRows) throws IOException {
        this.acceptedRows=acceptedRows;
        IOException failure=null;
        // Never publish completion until BOTH streams have flushed and closed successfully.
        try {snapshots.flush();events.flush();}catch(IOException e){errors++;failure=e;}
        try {snapshots.close();}catch(IOException e){errors++;if(failure==null)failure=e;else failure.addSuppressed(e);}
        try {events.close();}catch(IOException e){errors++;if(failure==null)failure=e;else failure.addSuppressed(e);}
        // Eligibility is sampled ONCE at the data boundary, before any final metadata I/O.
        // A later administrative wait timeout cannot retroactively make closed data incomplete.
        boolean timely=orderly.getAsBoolean();
        dataDrained=errors==0&&snapshotCount+eventCount==acceptedRows;
        if(!timely||!dataDrained)errors++;
        boolean complete=timely&&dataDrained&&dropped==0&&errors==0;
        try {persist(complete);}
        catch(IOException e) {if(failure==null)failure=e;else failure.addSuppressed(e);}
        if(failure!=null)throw failure;
    }
    private JsonObject evidence(boolean complete) throws IOException {
        var o=new JsonObject();o.addProperty("schema",1);o.addProperty("source_instance_id",instance);
        o.addProperty("completion_policy","data_closed_before_deadline_v1");o.addProperty("accepted_rows",acceptedRows);o.addProperty("data_drained",dataDrained);
        o.addProperty("state",complete?"complete":"incomplete");o.addProperty("dropped",dropped);o.addProperty("errors",errors);
        o.addProperty("snapshots",snapshotCount);o.addProperty("events",eventCount);o.addProperty("first_order",firstOrder);o.addProperty("last_order",lastOrder);
        for(String kind:new String[]{"snapshots","events"}) {
            Path file=path.resolve(kind+".jsonl");o.addProperty(kind+"_bytes",Files.exists(file)?Files.size(file):0);
        }
        return o;
    }
    private void persist(boolean complete) throws IOException {
        Path temporary=null;
        try {
            temporary=Files.createTempFile(path,"recording-",".tmp");Files.writeString(temporary,evidence(complete).toString());
            metadata.publish(temporary,manifest);
        } catch(IOException failure) {errors++;throw failure;}
        finally {if(temporary!=null)Files.deleteIfExists(temporary);}
    }
}

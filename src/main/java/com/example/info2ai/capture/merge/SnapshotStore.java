package com.example.info2ai.capture.merge;

import com.example.info2ai.model.*;
import com.example.info2ai.events.EventRing;
import com.example.info2ai.http.LongPollHub;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** 同一个锁内分配并发布 seq，避免把尚未发布的序号当成可消费游标。 */
public final class SnapshotStore implements AutoCloseable {
    private final AtomicLong sequence=new AtomicLong();
    private final String instanceId=UUID.randomUUID().toString();
    private final EventRing events;
    private final EventRing historyEvents=new EventRing(2048);
    private long totalEvents;
    private final long commitStart=System.nanoTime();
    private volatile com.example.info2ai.history.HistoryStore history;
    private volatile boolean replay;
    private volatile String sourceStatus="live";
    public synchronized void replayMode() { if(current!=null||history!=null)throw new IllegalStateException("source already initialized");replay=true;sourceStatus="loading";enableFresh(false); }
    public boolean replay() {return replay;}
    public String sourceStatus() {return sourceStatus;}
    public void sourceStatus(String value) {sourceStatus=value;}
    public synchronized void replayUnavailable() {current=null;publication=null;sourceStatus="unavailable";warning("replay_unavailable");}
    public synchronized void publishRecorded(Snapshot sample,Facts source) {
        if(!replay)throw new IllegalStateException("not replay");
        current=new Snapshot(sample.v(),sequence.incrementAndGet(),sample.capturedAt(),sample.tick(),sample.detail(),
            sample.session(),sample.world(),sample.player(),sample.inventory(),sample.surroundings(),sample.progress(),null,
            sample.signals(),new Capabilities(false,false,false,false,false,null,false,false,false,false,false),sample.hashes(),source);
        publication=new Publication(sourceEpoch,current);
    }
    public synchronized void emitRecorded(GameEvent event,Facts source) {
        if(!replay)throw new IllegalStateException("not replay");
        var data=event.data()==null?new com.google.gson.JsonObject():event.data().toJson();data.add("replay",source.toJson());
        appendCommitted(new GameEvent(sequence.incrementAndGet(),event.t(),event.tick(),event.kind(),event.severity(),event.durationMs(),event.text(),Facts.fromJson(data),event.pos()));
        if("world_unloaded".equals(event.kind())) {current=null;publication=null;}
        polls.signal();
    }
    public synchronized void history(com.example.info2ai.history.HistoryStore value) { if(replay)throw new IllegalStateException("replay is read only");history=value; }
    public com.example.info2ai.history.HistoryStore history() { return history; }
    private final LongPollHub polls=new LongPollHub();
    private volatile Snapshot current;
    private record Publication(long epoch, Snapshot snapshot) { }
    /** 只由接纳服务端发布的路径创建，HTTP 不再另读可变 current。 */
    public static final class ServerPublication {
        private final long epoch;
        private final Snapshot snapshot;
        private ServerPublication(long epoch,Snapshot snapshot) { this.epoch=epoch;this.snapshot=snapshot; }
        public long epoch() { return epoch; }
        public Snapshot snapshot() { return snapshot; }
    }
    private volatile Publication publication;
    private final AtomicLong epoch = new AtomicLong();
    private long sourceEpoch;
    private final CaptureDiagnostics diagnostics=com.example.info2ai.capture.server.CaptureLog.diagnostics(epoch::get);
    public CaptureDiagnostics diagnostics() { return diagnostics; }
    private record ClientSlice(long epoch, Snapshot snapshot) { }
    private final java.util.concurrent.atomic.AtomicReference<ClientSlice> clientMailbox = new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.ScheduledExecutorService clientPublisher = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "info2ai-client-publisher"); thread.setDaemon(true); return thread;
    });
    public long worldEpoch() { return epoch.get(); }
    /** 客户端只执行 CAS；先隐藏旧快照，慢清理交给后台，绝不等待服务端锁。 */
    public void invalidateWorld(long expectedEpoch) {
        if(epoch.compareAndSet(expectedEpoch,expectedEpoch+1)&&staticSource!=null)staticSource.clear();
    }
    public void publishClient(Snapshot sample) { publishClient(sample, epoch.get()); }
    public void publishClient(Snapshot sample, long expectedEpoch) {
        if (epoch.get() == expectedEpoch) clientMailbox.set(new ClientSlice(expectedEpoch, sample));
    }
    private synchronized void drainClient() {
        long attemptedEpoch=epoch.get();
        try {
            reconcileEpoch();
            ClientSlice slice = clientMailbox.getAndSet(null);
            if (slice == null || slice.epoch() != sourceEpoch) return;
            Snapshot sample = slice.snapshot();
            // 已确认服务端身份之后才接纳客户端补充；旧世界/维度的数据不可混入。
            if (serverPart == null || !sameWorld(serverPart, sample)) return;
            clientPart = sample;
            Snapshot merged = merger.merge(serverPart, clientPart, staticPart,staticAvailable,staticConfidence);
            if (merged != null && epoch.get() == sourceEpoch) {
                publish(merged);diagnostics.success(attemptedEpoch,"capture_client_publish_failed");
            }
        } catch (RuntimeException failure) { diagnostics.failure(attemptedEpoch,"capture_client_publish_failed",failure); }
    }
    private static boolean sameWorld(Snapshot a, Snapshot b) {
        return a.session()!=null && b.session()!=null && a.session().worldId()!=null && Objects.equals(a.session().worldId(), b.session().worldId())
            && a.world()!=null && b.world()!=null && a.world().dimensionId()!=null && Objects.equals(a.world().dimensionId(), b.world().dimensionId());
    }
    private void reconcileEpoch() {
        if (sourceEpoch != epoch.get()) { resetSources(); sourceEpoch = epoch.get(); }
    }
    private Snapshot serverPart,clientPart;
    private StaticWorldInfo staticPart;
    private boolean staticAvailable;
    private String staticConfidence;
    private volatile com.example.info2ai.staticworld.CachedStaticWorldSource staticSource;
    private record GenerationDiagnostics(long epoch,Facts info) { }
    private volatile GenerationDiagnostics generationDiagnostics;
    public void staticGenerationContext(long expectedEpoch,Facts info) {
        if(epoch.get()==expectedEpoch)generationDiagnostics=new GenerationDiagnostics(expectedEpoch,info);
    }
    public void staticSource(com.example.info2ai.staticworld.CachedStaticWorldSource source) {staticSource=source;}
    public synchronized void clearStaticSource() {if(staticSource!=null)staticSource.clear();staticPart=null;staticAvailable=false;staticConfidence=null;}
    public synchronized ServerPublication publishServer(Snapshot sample,long expectedEpoch,com.example.info2ai.staticworld.StaticQuery query) {
        reconcileEpoch();if(expectedEpoch!=sourceEpoch)return null;
        staticPart=null;staticAvailable=false;staticConfidence=null;
        if(staticSource!=null&&query!=null&&query.epoch()==expectedEpoch&&sample.session()!=null&&sample.world()!=null&&sample.player()!=null
            &&Objects.equals(query.worldId(),sample.session().worldId())&&Objects.equals(query.seed(),sample.session().seed())
            &&Objects.equals(query.mc(),sample.session().mcVersion())&&Objects.equals(query.dimension(),sample.world().dimensionId())
            &&sample.player().pos()!=null&&query.y()==(int)Math.floor(sample.player().pos().get(1))) {
            var view=staticSource.read(query);staticAvailable=view.available();staticConfidence=view.confidence();
            var memory=history;
            if(view.ready())staticPart=com.example.info2ai.staticworld.StaticProjection.project(view.info(),query.dimension(),
                sample.player().pos().get(0),sample.player().pos().get(2),query.radius(),
                key->memory!=null&&memory.structureObserved(query.worldId(),expectedEpoch,key));
        }
        serverPart=sample;
        if(clientPart!=null&&!sameWorld(serverPart,clientPart))clientPart=null;
        publish(merger.merge(serverPart,clientPart,staticPart,staticAvailable,staticConfidence));
        return epoch.get()==expectedEpoch?new ServerPublication(expectedEpoch,current):null;
    }
    private final SnapshotMerger merger=new SnapshotMerger();
    public synchronized ServerPublication publishServer(Snapshot sample) { return publishServer(sample, epoch.get()); }
    public synchronized ServerPublication publishServer(Snapshot sample, long expectedEpoch) {
        return publishServer(sample,expectedEpoch,null);
    }
    public com.google.gson.JsonObject staticMetrics() {
        var current=staticSource;var metrics=current==null?new com.google.gson.JsonObject():current.metrics();
        var context=generationDiagnostics;
        if(context!=null&&context.epoch()==epoch.get()&&context.info()!=null)metrics.add("generation_context_detail",context.info().toJson());
        return metrics;
    }
    private volatile boolean paused;
    private record EpochStats(long epoch,PerfStats server,PerfStats client,PerfStats screenshot,PerfStats eventEngine,PerfStats signalEngine) {
        EpochStats(long epoch) { this(epoch,new PerfStats(),new PerfStats(),new PerfStats(),new PerfStats(),new PerfStats()); }
    }
    private final java.util.concurrent.atomic.AtomicReference<EpochStats> stats = new java.util.concurrent.atomic.AtomicReference<>(new EpochStats(0));
    private EpochStats currentStats() {
        while(true) {
            long currentEpoch=epoch.get();EpochStats current=stats.get();
            if(current.epoch()==currentEpoch)return current;
            if(current.epoch()>currentEpoch)continue; // 重新读代数，绝不把新 holder 回写成旧代数。
            stats.compareAndSet(current,new EpochStats(currentEpoch));
        }
    }
    public PerfStats perfStats() { return currentStats().server(); }
    public PerfStats clientPerfStats() { return currentStats().client(); }
    public PerfStats screenshotPerfStats() { return currentStats().screenshot(); }
    public void recordServerCapture(long expectedEpoch,long us) {
        EpochStats current=currentStats();if(current.epoch()==expectedEpoch)current.server().record(us);
    }
    public void recordClientCapture(long expectedEpoch,long us) {
        EpochStats current=currentStats();if(current.epoch()==expectedEpoch)current.client().record(us);
    }
    public void recordScreenshot(long expectedEpoch,long us) {
        EpochStats current=currentStats();if(current.epoch()==expectedEpoch)current.screenshot().record(us);
    }
    public PerfStats eventPerfStats() { return currentStats().eventEngine(); }
    public PerfStats signalPerfStats() { return currentStats().signalEngine(); }
    public void recordEventEngine(long expectedEpoch,long us) { EpochStats current=currentStats();if(current.epoch()==expectedEpoch)current.eventEngine().record(us); }
    public void recordSignalEngine(long expectedEpoch,long us) { EpochStats current=currentStats();if(current.epoch()==expectedEpoch)current.signalEngine().record(us); }
    private final Set<String> warnings=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ArrayDeque<FreshRequest> freshRequests=new ArrayDeque<>();
    private final Set<FreshRequest> pendingFresh=new HashSet<>();
    private boolean freshEnabled;
    public List<String> warnings() {
        Set<String> combined=new TreeSet<>(warnings);if(history!=null)combined.addAll(history.warnings());combined.addAll(diagnostics.warnings());
        var staticState=staticMetrics();if(staticState.has("warning"))combined.add(staticState.get("warning").getAsString());
        Snapshot snapshot=current();
        if(snapshot!=null && snapshot.capabilities()!=null && Boolean.TRUE.equals(snapshot.capabilities().clientCapture())
            && snapshot.session()!=null && snapshot.session().paused()!=null)combined.remove("capture_client_pause_unavailable");
        return List.copyOf(combined);
    }
    public void warning(String code) { warnings.add(code); }
    public void clearWarning(String code) { warnings.remove(code); }
    public synchronized void enableFresh(boolean enabled) {
        freshEnabled=enabled;
        if(!enabled) {
            pendingFresh.forEach(request->request.finish(null));
            pendingFresh.clear();freshRequests.clear();
        }
    }
    public synchronized FreshRequest requestFresh() { return requestFresh(epoch.get()); }
    public synchronized FreshRequest requestFresh(long expectedEpoch) {
        pendingFresh.removeIf(FreshRequest::isDone);
        freshRequests.removeIf(FreshRequest::isDone);
        Snapshot snapshot=current();
        FreshRequest request=new FreshRequest(expectedEpoch,snapshot==null?-1:snapshot.seq());
        if(epoch.get()!=expectedEpoch || !freshEnabled || snapshot==null || pendingFresh.size()>=16) { request.finish(null);return request; }
        pendingFresh.add(request);freshRequests.add(request);return request;
    }
    /** 取出后仍保留 pending 跟踪，退出时可以结束在途请求。 */
    public synchronized List<FreshRequest> takeFreshRequests() { return takeFreshRequests(epoch.get()); }
    public synchronized List<FreshRequest> takeFreshRequests(long expectedEpoch) {
        if(epoch.get()!=expectedEpoch)return List.of();
        List<FreshRequest> pending=new ArrayList<>();
        FreshRequest request;
        while((request=freshRequests.poll())!=null) {
            if(!request.isDone()) pending.add(request);else pendingFresh.remove(request);
        }
        return pending;
    }
    public synchronized void completeFresh(List<FreshRequest> requests,ServerPublication accepted) {
        for(FreshRequest request:requests) {
            boolean valid=accepted!=null && accepted.epoch()==request.epoch() && epoch.get()==request.epoch()
                && accepted.snapshot().seq()>request.afterSeq();
            request.finish(valid?accepted:null);pendingFresh.remove(request);
        }
    }
    public SnapshotStore() { this(512); }
    public SnapshotStore(int capacity) {
        events=new EventRing(capacity);
        clientPublisher.scheduleWithFixedDelay(()->{
            drainClient();
            // 离开合并锁后才可能发生日志 I/O。
            long loggingEpoch=epoch.get();
            try { com.example.info2ai.capture.server.CaptureLog.flush(); }
            catch(RuntimeException logFailure) { diagnostics.failure(loggingEpoch,"capture_log_sink_failed",logFailure); }
        },50,50,java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    public synchronized void publish(Snapshot sample) {
        if(replay)throw new IllegalStateException("replay excludes live publication");
        HistoryInfo publishedHistory=history==null||!history.persistent()||sample.session()==null
            ?sample.history():history.snapshot(sample.session().worldId(),sourceEpoch);
        Capabilities capabilities=sample.capabilities();
        // The persistent contribution arrives after source merge; advertise the final payload.
        if(capabilities!=null&&!Objects.equals(capabilities.history(),publishedHistory!=null))
            capabilities=new Capabilities(capabilities.serverCapture(),capabilities.clientCapture(),capabilities.screenshot(),
                capabilities.staticWorldAvailable(),capabilities.staticWorldReady(),capabilities.staticWorldConfidence(),publishedHistory!=null,
                capabilities.chatWrite(),capabilities.overlayWrite(),capabilities.tts(),capabilities.paused(),capabilities.director());
        current=new Snapshot(sample.v(),sequence.incrementAndGet(),System.currentTimeMillis(),sample.tick(),Detail.FULL,
            sample.session(),sample.world(),sample.player(),sample.inventory(),sample.surroundings(),sample.progress(),
            publishedHistory,sample.signals(),capabilities,sample.hashes(),null,null,sample.director());
        publication = new Publication(sourceEpoch, current);
        if(history!=null)history.offer(new com.example.info2ai.history.Commit(current.seq(),System.nanoTime()-commitStart,sourceEpoch,current,null));
    }
    public synchronized void emit(String kind,String severity,String text) {
        append(kind,severity,text,current);
        polls.signal();
    }
    public synchronized boolean emit(long expectedEpoch,String kind,String severity,String text) {
        if(epoch.get()!=expectedEpoch || current()==null)return false;
        append(kind,severity,text,current);polls.signal();return true;
    }
    public synchronized boolean emit(long expectedEpoch,com.example.info2ai.events.EventDraft event) {
        Snapshot context=current();
        if(epoch.get()!=expectedEpoch || context==null)return false;
        var data=event.data().toJson();
        if(context.session()!=null)data.addProperty("world_id",context.session().worldId());
        appendCommitted(new GameEvent(sequence.incrementAndGet(),System.currentTimeMillis(),event.tick(),event.kind(),event.severity(),event.durationMs(),event.text(),Facts.fromJson(data),event.pos()));
        polls.signal();return true;
    }
    private void append(String kind,String severity,String text,Snapshot context) {
        long seq=sequence.incrementAndGet();
        com.google.gson.JsonObject data=new com.google.gson.JsonObject();
        if(context!=null && context.session()!=null)data.addProperty("world_id",context.session().worldId());
        appendCommitted(new GameEvent(seq,System.currentTimeMillis(),context==null?0:context.tick(),kind,severity,0L,text,Facts.fromJson(data),context==null||context.player()==null?null:context.player().pos()));
    }
    public synchronized void clearWorld() {
        epoch.incrementAndGet(); resetSources(); sourceEpoch=epoch.get();
    }
    public synchronized void clearWorld(long expectedEpoch) {
        if (epoch.get() == expectedEpoch) clearWorld(); else reconcileEpoch();
    }
    private void resetSources() {
        Snapshot previous=current;
        clientMailbox.set(null);
        current=null;publication=null;serverPart=null;clientPart=null;staticPart=null;staticAvailable=false;staticConfidence=null;generationDiagnostics=null;merger.clear();
        if(staticSource!=null)staticSource.clear();
        enableFresh(false);currentStats();warnings.clear();paused=false;
        if(previous!=null&&!replay)append("world_unloaded","info","退出存档",previous);
        polls.closeAll();
    }
    public Snapshot current() { Publication value=publication; return value!=null && value.epoch()==epoch.get()?value.snapshot():null; }
    public synchronized long sequence() { return sequence.get(); }
    public String instanceId() { return instanceId; }
    private void appendCommitted(GameEvent event) { events.append(event);historyEvents.append(event);totalEvents++;
        if(history!=null)history.offer(new com.example.info2ai.history.Commit(event.seq(),System.nanoTime()-commitStart,sourceEpoch,null,event));
    }
    public EventRing events() { return events; }
    public EventRing historyEvents() { return historyEvents; }
    public synchronized long totalEvents() { return totalEvents; }
    public synchronized void clearHistory() { events.clear();historyEvents.clear();polls.signal(); }
    public LongPollHub polls() { return polls; }
    public boolean paused() { return paused; }
    public void paused(boolean value) { paused=value; }
    @Override public void close() { clientPublisher.shutdownNow();if(staticSource!=null)staticSource.close(); clearWorld();if(history!=null)history.close(); }
}

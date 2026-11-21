package com.example.info2ai.staticworld;
import com.example.info2ai.model.StaticWorldInfo;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.slf4j.LoggerFactory;

public final class CachedStaticWorldSource implements AutoCloseable {
    public record View(boolean available,boolean ready,StaticWorldInfo info,String error,String confidence) { }
    private record Work(long generation,long lifecycle,StaticQuery query,StaticQuery.Key key,boolean availableHint) { }
    private record Entry(StaticWorldInfo info,long expires,long lifecycle,int budgetRetries,long nextCalculation) { }
    private record Status(long workGeneration,boolean available,String error,int failures,long retryAt) { }
    private record Scope(long lifecycle,long epoch,String worldId,String dimension,String generation,int radius) { }
    private record Outage(Scope scope,int failures,long retryAt) { }
    private final StaticWorldSource source;
    private final String availableConfidence;
    private final AtomicLong generations=new AtomicLong(),attempts=new AtomicLong();
    private final AtomicLong lifecycle=new AtomicLong();
    private final AtomicReference<Work> latest=new AtomicReference<>();
    private volatile Status status;
    private volatile Outage outage;
    private volatile Map<StaticQuery.Key,Entry> cache=Map.of();
    private volatile Entry lastResult;
    private final ScheduledExecutorService worker;
    private volatile boolean closed;
    private long lastLog;
    private static final long TTL=TimeUnit.MINUTES.toNanos(1);
    private static final long EMPTY_BUDGET_RETRY_DELAY=TimeUnit.MILLISECONDS.toNanos(500);
    private static final int EMPTY_BUDGET_RETRIES=2;

    public CachedStaticWorldSource(StaticWorldSource source) {
        this.source=Objects.requireNonNull(source);
        availableConfidence=source.availableConfidence();
        worker=Executors.newSingleThreadScheduledExecutor(task->{var t=new Thread(task,"info2ai-static-worker");t.setDaemon(true);return t;});
        worker.scheduleWithFixedDelay(this::refresh,0,25,TimeUnit.MILLISECONDS);
    }
    /** Pure bounded memory work. No future, socket, filesystem, or worker lock. */
    public View read(StaticQuery query) {
        if(closed)return new View(false,false,null,"static_source_closed",null);
        Work current=latest.get();var key=query.key();
        long scope=lifecycle.get();
        if(current==null||current.lifecycle()!=scope||!current.key().equals(key)||current.query().supported()!=query.supported()) {
            Status previous=status;
            boolean connected=matches(previous,current)&&previous.available()
                &&query.supported();
            Work next=new Work(generations.incrementAndGet(),scope,query,key,connected);
            if(latest.compareAndSet(current,next)) {
                current=next;
                if(scope!=lifecycle.get())latest.compareAndSet(next,null);
            } else current=latest.get();
        }
        if(current==null||current.lifecycle()!=lifecycle.get()||!current.key().equals(key))return new View(false,false,null,null,null);
        if(!query.supported())return new View(false,false,null,query.enabled()?"static_unsupported_generation_or_dimension":null,null);
        Status state=status;boolean matching=matches(state,current);
        Outage failure=outage;boolean failed=matches(failure,current);
        Entry entry=cache.get(key);boolean ready=entry!=null&&entry.lifecycle()==lifecycle.get()&&entry.expires()>System.nanoTime();
        boolean available=availableConfidence!=null||!failed&&(matching?state.available():current.availableHint());
        return new View(available,ready,ready?entry.info():null,
            failed?"static_world_unavailable":matching?state.error():null,available?availableConfidence:null);
    }
    private static Scope scope(Work work) {
        var q=work.query();return new Scope(work.lifecycle(),q.epoch(),q.worldId(),q.dimension(),q.generation(),q.radius());
    }
    private static boolean matches(Outage failure,Work work) {
        return failure!=null&&work!=null&&failure.scope().equals(scope(work));
    }
    private static boolean matches(Status state,Work work) {
        return state!=null&&work!=null&&state.workGeneration()==work.generation();
    }
    private boolean owns(Work work) {return !closed&&latest.get()==work&&work.lifecycle()==lifecycle.get();}
    private void refresh() {
        Work work=latest.get();if(work==null||!owns(work)||!work.query().supported())return;
        long now=System.nanoTime();Status state=status;
        Outage previous=outage;
        if(matches(previous,work)&&now<previous.retryAt())return;
        if(matches(state,work)&&now<state.retryAt())return;
        Entry cached=cache.get(work.key());
        if(cached!=null&&cached.lifecycle()!=work.lifecycle())cached=null;
        try {
            attempts.incrementAndGet();
            if(!source.isAvailable(work.query()))throw new java.io.IOException("static_source_unavailable");
            if(!owns(work))return;
            outage=null;
            status=new Status(work.generation(),true,null,0,0);
            if(cached==null||cached.expires()<=now||cached.nextCalculation()<=now) {
                StaticWorldInfo result=source.getStatic(work.query());
                if(result==null)throw new java.io.IOException("static_empty_response");
                if(!owns(work))return;
                long completed=System.nanoTime();
                int retries=cached==null||cached.expires()<=now?0:cached.budgetRetries()+1;
                long nextCalculation=retries<EMPTY_BUDGET_RETRIES&&budgetEmpty(result)?completed+EMPTY_BUDGET_RETRY_DELAY:Long.MAX_VALUE;
                cached=new Entry(result,completed+TTL,work.lifecycle(),retries,nextCalculation);
                lastResult=cached;
            }
            if(!owns(work))return;
            LinkedHashMap<StaticQuery.Key,Entry> updated=new LinkedHashMap<>(cache);
            updated.entrySet().removeIf(e->e.getValue().expires()<=now);
            updated.remove(work.key());updated.put(work.key(),cached);
            while(updated.size()>32)updated.remove(updated.keySet().iterator().next());
            cache=Collections.unmodifiableMap(updated);
            status=new Status(work.generation(),true,null,0,Math.min(cached.nextCalculation(),System.nanoTime()+TimeUnit.SECONDS.toNanos(5)));
        } catch(Exception|LinkageError failure) {
            if(!owns(work))return;
            int count=matches(previous,work)?previous.failures()+1:1;
            long retry=System.nanoTime()+TimeUnit.SECONDS.toNanos(Math.min(30,1L<<Math.min(5,count-1)));
            outage=new Outage(scope(work),count,retry);
            status=new Status(work.generation(),false,"static_world_unavailable",count,retry);
            if(now-lastLog>=TimeUnit.SECONDS.toNanos(1)) {
                lastLog=now;LoggerFactory.getLogger("info2ai.static").warn("Static source unavailable ({})",failure.getClass().getSimpleName());
            }
        }
    }
    private static boolean budgetEmpty(StaticWorldInfo info) {
        if(!info.structures().isEmpty()||info.coverage()==null)return false;
        var truncated=info.coverage().toJson().get("truncated_by_budget");
        return truncated!=null&&truncated.isJsonPrimitive()&&truncated.getAsBoolean();
    }
    public void clear() {lifecycle.incrementAndGet();latest.set(null);generations.incrementAndGet();cache=Map.of();lastResult=null;status=null;outage=null;}
    public JsonObject metrics() {
        var out=new JsonObject();Work work=latest.get();Status state=status;
        long currentLifecycle=lifecycle.get();
        if(work!=null&&work.lifecycle()!=currentLifecycle)work=null;
        boolean matching=matches(state,work);
        Outage failure=outage;boolean failed=matches(failure,work);
        out.addProperty("source",source.describe());
        out.addProperty("available",work!=null&&work.lifecycle()==lifecycle.get()&&work.query().supported()
            &&(availableConfidence!=null||!failed&&(matching?state.available():work.availableHint())));
        out.addProperty("attempts",attempts.get());out.addProperty("failures",failed?failure.failures():matching?state.failures():0);
        long retry=failed?failure.retryAt():matching?state.retryAt():0;
        out.addProperty("retry_in_ms",Math.max(0,(retry-System.nanoTime())/1_000_000));
        out.addProperty("cache_entries",cache.values().stream().filter(entry->entry.lifecycle()==currentLifecycle).count());
        out.addProperty("query_generation",work==null?0:work.generation());
        Entry measured=lastResult;
        if(measured!=null&&measured.lifecycle()==lifecycle.get()&&measured.info().coverage()!=null) {
            var coverage=measured.info().coverage().toJson();
            for(String metric:List.of("elapsed_ms","chunks_checked","truncated_by_budget")) {
                if(coverage.has(metric))out.add(metric,coverage.get(metric));
            }
        }
        if(failed)out.addProperty("warning","static_world_unavailable");
        else if(matching&&state.error()!=null)out.addProperty("warning",state.error());
        if(work!=null&&work.query().enabled()&&!work.query().supported()) {
            out.addProperty("warning","static_unsupported_generation_or_dimension");
            out.addProperty("generation_context",work.query().generation());
        }
        return out;
    }
    public void close() {closed=true;clear();worker.shutdownNow();}
}

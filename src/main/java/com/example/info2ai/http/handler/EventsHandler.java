package com.example.info2ai.http.handler;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.events.EventRing;
import com.example.info2ai.http.*;
import com.example.info2ai.model.Detail;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public final class EventsHandler implements HttpHandler {
    private final SnapshotStore store;
    private final boolean history;
    public EventsHandler(SnapshotStore store,boolean history) { this.store=store;this.history=history; }
    @Override public void handle(HttpExchange exchange) throws IOException {
        Map<String,String> query=Query.parse(exchange);
        long started=System.nanoTime();
        long since=Query.number(query,"since",0,0,Long.MAX_VALUE);
        long wait=Query.number(query,"wait_ms",0,0,60000);
        int limit=(int)Query.number(query,"limit",history?256:32,1,history?2048:256);
        Set<String> kinds=Query.csv(query.get("kinds"));
        if(since==0 && !history)since=store.sequence();
        if(query.containsKey("instance_id") && !store.instanceId().equals(query.get("instance_id")))throw new ApiException(409,"instance_changed");
        if(since>store.sequence())throw new ApiException(409,"cursor_ahead");
        EventRing.Page page=(history?store.historyEvents():store.events()).read(since,limit,kinds);
        if(!history && page.events().isEmpty() && wait>0 && store.current()!=null && !page.lost()) {
            CompletableFuture<Void> future;
            try { future=store.polls().register(since,wait); }
            catch(RejectedExecutionException rejected) { throw new ApiException(429,"too_many_waiters"); }
            try {
                page=store.events().read(since,limit,kinds);
                if(page.events().isEmpty() && store.current()!=null) {
                    try { future.get(wait,TimeUnit.MILLISECONDS); }
                    catch(TimeoutException timeout) { /* 正常心跳。 */ }
                    catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    catch(ExecutionException failure) { throw new IllegalStateException("等待事件失败",failure); }
                }
                page=store.events().read(since,limit,kinds);
            } finally { store.polls().remove(future); }
        }
        JsonObject body=new JsonObject();
        body.addProperty("seq",store.sequence());
        body.addProperty("instance_id",store.instanceId());
        JsonArray events=new JsonArray();page.events().forEach(event->events.add(event.toJson(Detail.FULL)));
        body.add("events",events);
        body.addProperty("next_since",page.nextSince());
        body.addProperty("oldest_seq",page.oldestSeq());
        body.addProperty("lost",page.lost());
        body.add("dropped_before_since",page.lost()?JsonNull.INSTANCE:new JsonPrimitive(0));
        body.addProperty("has_more",page.hasMore());
        body.addProperty("waited_ms",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        body.addProperty("heartbeat",!history && page.events().isEmpty());
        body.addProperty("in_world",store.current()!=null);
        exchange.getResponseHeaders().set("X-Info2ai-Seq",body.get("seq").getAsString());
        Json.write(exchange,200,body);
    }
}

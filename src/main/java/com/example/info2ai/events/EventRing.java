package com.example.info2ai.events;

import com.example.info2ai.model.GameEvent;
import java.util.*;

/** 仅存不可变事件；不修改已发布 seq 的内容。 */
public final class EventRing {
    private final int capacity;
    private final ArrayDeque<GameEvent> events=new ArrayDeque<>();
    private long latest,evictedThrough,dropped;
    public EventRing(int capacity) {
        if(capacity<1)throw new IllegalArgumentException("事件容量必须大于零");
        this.capacity=capacity;
    }
    public synchronized void append(GameEvent event) {
        if(event.seq()<=latest)throw new IllegalArgumentException("事件 seq 必须递增");
        latest=event.seq();
        events.addLast(event);
        if(events.size()>capacity) { evictedThrough=events.removeFirst().seq(); dropped++; }
    }
    public synchronized Page read(long since,int limit,Set<String> kinds) {
        if(limit<1 || limit>2048)throw new IllegalArgumentException("事件 limit 越界");
        List<GameEvent> selected=new ArrayList<>();
        boolean more=false;
        long cursor=since;
        for(GameEvent event:events) {
            if(event.seq()<=since)continue;
            boolean match=kinds.isEmpty() || kinds.contains(event.kind());
            if(match && selected.size()==limit) { more=true;break; }
            cursor=event.seq();
            if(match)selected.add(event);
        }
        if(!more)cursor=Math.max(cursor,latest);
        return new Page(List.copyOf(selected),cursor,more,events.isEmpty()?latest:events.getFirst().seq(),evictedThrough>0 && since<evictedThrough);
    }
    public synchronized void clear() { events.clear();evictedThrough=latest; }
    public synchronized int size() { return events.size(); }
    public synchronized long dropped() { return dropped; }
    public record Page(List<GameEvent> events,long nextSince,boolean hasMore,long oldestSeq,boolean lost) { }
}

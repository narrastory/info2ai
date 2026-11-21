package com.example.info2ai;

import com.example.info2ai.events.EventRing;
import com.example.info2ai.model.GameEvent;
import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class EventRingTest {
    private GameEvent event(long seq,String kind) { return new GameEvent(seq,0L,0L,kind,"info",0L,kind,null,null); }
    @Test public void filteringAndPaginationDoNotSkipMatchingEvents() {
        EventRing ring=new EventRing(8);
        ring.append(event(2,"damage"));ring.append(event(5,"other"));ring.append(event(9,"damage"));
        var first=ring.read(1,1,Set.of("damage"));
        assertEquals(1,first.events().size());assertTrue(first.hasMore());
        assertEquals(5,first.nextSince());
        var next=ring.read(first.nextSince(),1,Set.of("damage"));
        assertEquals(9,next.events().getFirst().seq().longValue());assertFalse(next.hasMore());
    }
    @Test public void snapshotSequenceGapsAreNotReportedAsLostEvents() {
        EventRing ring=new EventRing(2);
        ring.append(event(4,"a"));ring.append(event(10,"a"));
        assertFalse(ring.read(1,2,Set.of()).lost());
        ring.append(event(20,"a"));
        assertTrue(ring.read(1,2,Set.of()).lost());
        assertFalse(ring.read(4,2,Set.of()).lost());
    }
}

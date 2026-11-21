package com.example.info2ai;
import com.example.info2ai.capture.client.SoundHistory;
import org.junit.Test;
import static org.junit.Assert.*;
public class SoundHistoryTest {
    @Test public void excludesDistantMusicAndRelativeSoundsDeduplicatesAndExpires() {
        var history = new SoundHistory();
        assertNull(history.recent(0,0,0,0));
        history.observe("music", true, false, 0,0,0,0);
        history.observe("relative", false, true, 0,0,0,0);
        history.observe("far", false, false, 33,0,0,0);
        history.observe("explosion", false, false, 0,0,-10,100);
        history.observe("explosion", false, false, 0,0,-10,400);
        var recent = history.recent(0,0,0,500);
        assertNotNull(recent); assertEquals(1, recent.size()); assertEquals("explosion", recent.getFirst().id());
        assertEquals(100, recent.getFirst().at());
        assertTrue(history.recent(0,0,0,10101).isEmpty());
        history.clear(); assertNull(history.recent(0,0,0,10101));
    }
    @Test public void retainsAtMostTwelveRecentNearbySounds() {
        var history = new SoundHistory();
        for(int i=0;i<40;i++) history.observe("sound"+i,false,false,0,0,1,i*20);
        var recent=history.recent(0,0,0,1000);
        assertNotNull(recent); assertEquals(12,recent.size());assertEquals("sound39",recent.getFirst().id());
    }
}

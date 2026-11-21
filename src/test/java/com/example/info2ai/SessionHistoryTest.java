package com.example.info2ai;
import com.example.info2ai.capture.server.SessionHistory;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SessionHistoryTest {
 @Test public void dimensionTransitionsAreNotDoubleCountedAndDeathAgeUsesElapsedTicks() {
   var history=new SessionHistory();history.observe(0,"minecraft:overworld",List.of(1.,64.,2.),1);
   history.observe(4,"minecraft:the_nether",List.of(2.,70.,3.),22);
   history.observe(8,"minecraft:the_nether",List.of(2.,70.,3.),99);
   history.died(8,"lava",2,3,99);
   var state=history.snapshot(48);
   assertEquals(1,state.dimensionSwitches().size());assertEquals(1,state.deathsThisSession().intValue());
   assertEquals(2,state.lastDeath().toJson().get("ago_s").getAsInt());assertEquals(2,state.sessionEvents().intValue());
 }
 @Test public void positionHistorySamplesAtTenSecondsAndKeepsNewestThirty() {
   var history=new SessionHistory();
   for(int tick=0;tick<=6400;tick+=4)history.observe(tick,"overworld",List.of((double)tick,0.,0.),1);
   var positions=history.snapshot(6400).recentPositions();assertEquals(30,positions.size());
   assertEquals(6400.,positions.getFirst().getFirst(),0);assertEquals(600.,positions.getLast().getFirst(),0);
 }
}

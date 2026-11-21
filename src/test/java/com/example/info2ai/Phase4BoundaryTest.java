package com.example.info2ai;
import com.example.info2ai.events.*;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.http.MockSnapshotFactory;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class Phase4BoundaryTest {
    @Test public void incompleteInputCoverageNeverProvesAfkAndPauseFreezesClock() {
        InputActivity input=new InputActivity();
        assertNull(input.idleTicks(2000));
        for(int channel=0;channel<5;channel++)input.input(channel,0);
        assertEquals(Long.valueOf(1801),input.idleTicks(1801));
        input.input(4,1801);
        assertEquals(Long.valueOf(0),input.idleTicks(1801));
        assertEquals(Long.valueOf(0),input.idleTicks(1801));
        input.reset();
        assertNull(input.idleTicks(4000));
    }
    @Test public void missingLoadedCellsAndPartialScanNeverProveBedAbsence() {
        PresenceScan scan=new PresenceScan(8);
        scan.step(4,i->false);
        assertNull(scan.result());
        scan.step(4,i->i==7?null:false);
        assertNull(scan.result());
        scan.reset();
        scan.step(8,i->false);
        assertEquals(Boolean.FALSE,scan.result());
        scan.reset();
        scan.step(1,i->true);
        assertEquals(Boolean.TRUE,scan.result());
    }
    @Test public void typedEventsRetainActualTickPositionDurationAndFenceOldEpoch() {
        try(SnapshotStore store=new SnapshotStore()) {
            store.publish(MockSnapshotFactory.create());
            long since=store.sequence();
            var data=EventData.of("amount",3,"nested",EventData.of("id","minecraft:fall"));
            var draft=new EventDraft(99,"player_damaged","warn","受到3点伤害",data,List.of(1d,2d,3d),123);
            assertTrue(store.emit(0,draft));
            var event=store.events().read(since,10,Set.of()).events().getFirst();
            assertEquals(Long.valueOf(99),event.tick());
            assertEquals(Long.valueOf(123),event.durationMs());
            assertEquals(List.of(1d,2d,3d),event.pos());
            assertEquals(3,event.data().toJson().get("amount").getAsInt());
            var copy=data.toJson();
            copy.addProperty("amount",999);
            assertEquals(3,event.data().toJson().get("amount").getAsInt());
            store.clearWorld();
            assertFalse(store.emit(0,draft));
        }
    }
    private static Observation miningFrame(long tick,int breaks,double x) {
        var b=Phase4RulesTest.baseline(tick);
        var actions=breaks==0?List.<Observation.Action>of():List.of(new Observation.Action(
            Observation.ActionType.BREAK,"minecraft:stone",breaks,0,null,"p",true,true,true,false,null,null));
        return new Observation(tick,b.playerId(),b.dimension(),List.of(x,64d,0d),b.vital(),b.env(),b.inv(),null,
            actions,List.of(),List.of(),List.of(),false,false);
    }
    private static void fastMining(ActivityInference activity,SignalEngine signals) {
        activity.observe(miningFrame(0,0,0));signals.evaluate(miningFrame(0,0,0),activity);
        for(int tick=1;tick<=121;tick++)activity.observe(miningFrame(tick,1,0));
    }
    @Test public void miningLongRequiresBreaksAcrossFiveMinutes() {
        ActivityInference activity=new ActivityInference();SignalEngine signals=new SignalEngine();
        activity.observe(miningFrame(0,0,0));signals.evaluate(miningFrame(0,0,0),activity);
        // 每两游戏秒真实BREAK一次，持续超过五分钟，而不是两头各一次。
        for(int tick=1;tick<=6041;tick+=40) {
            var frame=miningFrame(tick,1,0);activity.observe(frame);signals.evaluate(frame,activity);
        }
        var signal=signals.evaluate(miningFrame(6041,0,0),activity).stream()
            .filter(x->x.kind().equals("mining_long")).findFirst().orElseThrow();
        assertEquals(152,signal.detail().toJson().get("blocks").getAsInt());
        assertEquals(200,signal.detail().toJson().get("idle_timeout_ticks").getAsInt());
    }
    @Test public void stoppedMiningCannotCreateLongSignalWhileStanding() {
        ActivityInference activity=new ActivityInference();SignalEngine signals=new SignalEngine();fastMining(activity,signals);
        var stopped=miningFrame(6122,0,0);activity.observe(stopped);
        assertEquals("idle",activity.current().kind());
        assertFalse(signals.evaluate(stopped,activity).stream().anyMatch(x->x.kind().equals("mining_long")));
    }
    @Test public void stoppedMiningCannotCreateLongSignalWhileWalking() {
        ActivityInference activity=new ActivityInference();SignalEngine signals=new SignalEngine();fastMining(activity,signals);
        for(int tick=142;tick<=6122;tick+=20)activity.observe(miningFrame(tick,0,(tick-122)/20d));
        var stopped=miningFrame(6122,0,300);activity.observe(stopped);
        assertEquals("traveling",activity.current().kind());
        assertFalse(signals.evaluate(stopped,activity).stream().anyMatch(x->x.kind().equals("mining_long")));
    }
    @Test public void breakAfterLongGapStartsNewMiningInterval() {
        ActivityInference activity=new ActivityInference();SignalEngine signals=new SignalEngine();fastMining(activity,signals);
        var resumed=miningFrame(6122,1,0);activity.observe(resumed);
        assertEquals(1,activity.miningCount());assertEquals(6122,activity.miningSince());
        assertFalse(signals.evaluate(resumed,activity).stream().anyMatch(x->x.kind().equals("mining_long")));
    }
    @Test public void miningIdleBoundaryUsesGameTicksAndPauseDoesNotAdvanceIt() {
        ActivityInference activity=new ActivityInference();activity.observe(miningFrame(1,1,0));
        activity.observe(miningFrame(200,0,0));assertEquals(1,activity.miningCount());
        activity.observe(miningFrame(200,0,0));assertEquals(1,activity.miningCount());
        activity.observe(miningFrame(201,0,0));assertEquals(0,activity.miningCount());
    }
    @Test public void translationUsesActualVanillaIdsAndFallsBackToPath() throws Exception {
        VanillaLocaleFixture.initialize();
        try(var stream=getClass().getResourceAsStream("/fixtures/phase4-vanilla-ids.json")) {
            var ids=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            assertEquals(116,ids.size());
            for(var id:ids)assertNotEquals(id.getAsString().substring(10),com.example.info2ai.util.NameZh.get(id.getAsString()));
        }
        assertEquals("missing_id",com.example.info2ai.util.NameZh.get("custom:missing_id"));
    }
    @Test public void commonInventoryItemNamesWinFlatRegistryCollisions() throws Exception {
        VanillaLocaleFixture.initialize();
        assertEquals("鸡蛋",com.example.info2ai.util.NameZh.get("minecraft:egg"));
        assertEquals("末影珍珠",com.example.info2ai.util.NameZh.get("minecraft:ender_pearl"));
        assertEquals("小麦",com.example.info2ai.util.NameZh.get("minecraft:wheat"));
        var items=List.of(new com.example.info2ai.capture.server.InventorySummary.Entry("minecraft:egg",1,0),
            new com.example.info2ai.capture.server.InventorySummary.Entry("minecraft:ender_pearl",1,0),
            new com.example.info2ai.capture.server.InventorySummary.Entry("minecraft:wheat",1,0));
        assertEquals("1 鸡蛋、1 末影珍珠、1 小麦",com.example.info2ai.capture.server.InventorySummary.summary(items));
    }

}

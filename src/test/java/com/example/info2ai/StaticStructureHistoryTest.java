package com.example.info2ai;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.events.*;
import com.example.info2ai.history.HistoryStore;
import com.example.info2ai.model.*;
import com.example.info2ai.staticworld.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class StaticStructureHistoryTest {
    @Test public void unknownVariantSurvivesCodecEventSignalAndLaterRefinementWithoutDuplicateHistory() throws Exception {
        var unknown=StructureInfo.fromJson(com.google.gson.JsonParser.parseString("""
            {"type":"minecraft:ruined_portal","structure_id":null,"variant_status":"unknown",
             "x":100,"z":0,"confidence":"medium"}
            """).getAsJsonObject());
        assertEquals("unknown",unknown.toJson(Detail.NORMAL).get("variant_status").getAsString());
        assertTrue(unknown.toJson(Detail.NORMAL).get("structure_id").isJsonNull());
        var knownJson=unknown.toJson(Detail.NORMAL);knownJson.addProperty("structure_id","minecraft:ruined_portal_ocean");
        knownJson.addProperty("variant_status","resolved");var known=StructureInfo.fromJson(knownJson);
        assertEquals(StaticProjection.key("minecraft:overworld",unknown),StaticProjection.key("minecraft:overworld",known));
        Path root=Files.createTempDirectory(Path.of("build"),"variant-history-");
        for(var item:List.of(unknown,known))try(var store=new SnapshotStore();var history=new HistoryStore(root,store,null,128)) {
            var info=new StaticWorldInfo("cubiomes","medium",null,null,List.of(item),List.of());
            var projected=StaticProjection.project(info,"minecraft:overworld",90,0,2000,k->false);
            assertEquals(item.toJson(Detail.NORMAL).get("variant_status"),projected.structures().getFirst().toJson(Detail.NORMAL).get("variant_status"));
            var observation=new Observation(1,"p","minecraft:overworld",List.of(90d,64d,0d),null,null,null,null,
                List.of(),List.of(),List.of(),StaticProjection.observations(projected,"minecraft:overworld",90,0),true,false,false,false);
            var event=new EventEngine().tick(observation,0).stream().filter(e->e.kind().equals("structure_found")).findFirst().orElseThrow();
            var data=event.data().toJson();
            assertEquals("minecraft:ruined_portal",data.get("type").getAsString());
            assertEquals(item.toJson(Detail.NORMAL).get("structure_id"),data.get("structure_id"));
            assertEquals(item.toJson(Detail.NORMAL).get("variant_status"),data.get("variant_status"));
            assertEquals("cubiomes",data.get("source").getAsString());assertEquals("medium",data.get("confidence").getAsString());
            var signal=new SignalEngine().evaluate(observation,null).stream().filter(s->s.kind().equals("structure_nearby")).findFirst().orElseThrow();
            assertEquals(data.get("structure_id"),signal.detail().toJson().get("structure_id"));
            assertEquals(data.get("variant_status"),signal.detail().toJson().get("variant_status"));
            store.publish(PersistentHistoryTest.sample(PersistentHistoryTest.A,1));store.emit(store.worldEpoch(),event);history.flush();
            var visits=history.snapshot(PersistentHistoryTest.A).visitedStructures();assertEquals(1,visits.size());
            assertEquals(data.get("structure_id"),visits.getFirst().toJson().get("structure_id"));
            var revisited=StaticProjection.project(info,"minecraft:overworld",90,0,2000,
                key->history.structureObserved(PersistentHistoryTest.A,store.worldEpoch(),key));
            assertTrue(revisited.structures().getFirst().visited());
        }
    }
    @Test public void predictionFlowsThroughTypedEventSignalAndStablePersistentVisitIdentity() throws Exception {
        var info=StaticProjection.project(CachedStaticWorldSourceTest.data(100),"minecraft:overworld",90,0,2000,k->false);
        var structures=StaticProjection.observations(info,"minecraft:overworld",90,0);assertEquals(1,structures.size());
        var observation=new Observation(1,"p","minecraft:overworld",List.of(90d,64d,0d),null,null,null,null,
                List.of(),List.of(),List.of(),structures,true,false,false,false);
        var event=new EventEngine().tick(observation,0).stream().filter(e->e.kind().equals("structure_found")).findFirst().orElseThrow();
        var facts=event.data().toJson();assertEquals("cubiomes",facts.get("source").getAsString());
        assertEquals(100,facts.get("x").getAsInt());assertFalse(facts.has("y"));assertFalse(facts.get("absolute_first_known").getAsBoolean());
        String key=facts.get("key").getAsString();assertTrue(key.contains("minecraft:overworld"));
        var signal=new SignalEngine().evaluate(observation,null).stream().filter(s->s.kind().equals("structure_nearby")).findFirst().orElseThrow();
        assertEquals(key,signal.detail().toJson().get("key").getAsString());
        Path root=Files.createTempDirectory(Path.of("build"),"static-history-");
        for(int run=0;run<2;run++)try(var store=new SnapshotStore();var history=new HistoryStore(root,store,null,128)) {
            store.publish(PersistentHistoryTest.sample(PersistentHistoryTest.A,run+1));
            var changed=facts.deepCopy();changed.addProperty("dist",run==0?10:22);changed.addProperty("dir",run==0?"E":"W");
            store.emit(store.worldEpoch(),new EventDraft(run+1,"structure_found","info","预测结构附近",Facts.fromJson(changed),null,0));
            history.flush();var visits=history.snapshot(PersistentHistoryTest.A).visitedStructures();assertEquals(1,visits.size());
            assertEquals(key,visits.getFirst().toJson().get("key").getAsString());
        }
    }
}

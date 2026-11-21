package com.example.info2ai;
import com.example.info2ai.capture.merge.SnapshotMerger;
import com.example.info2ai.model.*;
import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SnapshotMergerTest {
 private Snapshot sample(boolean server,boolean client,int health,String screen,String biome) {
   var p=new JsonObject();p.addProperty("health",health);p.addProperty("screen",screen);
   var s=new JsonObject();s.addProperty("biome",biome);s.add("sounds",new JsonArray());
   return Snapshot.builder().player(PlayerInfo.fromJson(p)).surroundings(SurroundingsInfo.fromJson(s))
    .capabilities(new Capabilities(server,client,false,false,false,null,false,false,false,false,false)).build();
 }
 @Test public void clientOverlaysOnlyOwnedFieldsWhileServerHealthAndBiomeRemainAuthoritative() {
   var merged=new SnapshotMerger().merge(sample(true,false,20,"server","minecraft:plains"),sample(false,true,4,"chest","client:wrong"),null);
   assertEquals(20,merged.player().health(),0);assertEquals("chest",merged.player().screen());assertEquals("minecraft:plains",merged.surroundings().biome());
   assertTrue(merged.capabilities().serverCapture());assertTrue(merged.capabilities().clientCapture());
 }
 @Test public void actualClientPauseOverlaysServerUnknownWithoutReplacingSessionIdentity() {
   var serverJson=sample(true,false,20,"server","minecraft:plains").toJson();
   var session=new JsonObject();session.addProperty("world_id","world-a");session.addProperty("seed","123");
   serverJson.add("session",session);
   var clientJson=sample(false,true,4,"chest","minecraft:plains").toJson();
   var pause=new JsonObject();pause.addProperty("world_id","world-a");pause.addProperty("paused",true);pause.addProperty("seed","WRONG");
   clientJson.add("session",pause);
   var merged=new SnapshotMerger().merge(Snapshot.fromJson(serverJson),Snapshot.fromJson(clientJson),null);
   assertEquals(Boolean.TRUE,merged.session().paused());
   assertEquals("123",merged.session().seed());
 }
 @Test public void clientFallbackIsMarkedLowConfidenceAndHashesTrackOnlyChangedSections() {
   var merger=new SnapshotMerger();var client=sample(false,true,4,"inventory","minecraft:plains");
   var a=merger.merge(null,client,null);var b=merger.merge(null,client,null);
   assertEquals("low",a.toJson().getAsJsonObject("player").get("confidence").getAsString());
   assertFalse(a.capabilities().serverCapture());assertEquals(a.hashes(),b.hashes());
   var c=merger.merge(null,sample(false,true,7,"inventory","minecraft:plains"),null);
   assertNotEquals(a.hashes().get("player"),c.hashes().get("player"));assertEquals(a.hashes().get("surroundings"),c.hashes().get("surroundings"));
 }
}

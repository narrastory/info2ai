package com.example.info2ai;
import com.example.info2ai.capture.server.ContainerReader;
import com.example.info2ai.model.*;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ContainerReaderTest {
 @Test public void pendingLootNeverCallsInventoryGetter() {
   var result=ContainerReader.read("minecraft:chests/simple_dungeon",27,i->{fail("would generate loot");return null;});
   assertFalse(result.known());assertTrue(result.items().isEmpty());
 }
 @Test public void normalSummaryTruncatesIdsButFullRetainsCountsAndSlots() {
   JsonObject input=new JsonObject();JsonArray containers=new JsonArray();JsonObject c=new JsonObject();JsonArray items=new JsonArray();
   for(int i=0;i<14;i++) { JsonObject item=new JsonObject();item.addProperty("id","custom:item_"+i);item.addProperty("count",i+1);item.addProperty("slot",i);items.add(item); }
   c.add("items",items);containers.add(c);input.add("block_entities",containers);
   var surroundings=SurroundingsInfo.fromJson(input);
   var normal=surroundings.toJson(Detail.NORMAL).getAsJsonArray("block_entities").get(0).getAsJsonObject();
   assertEquals(12,normal.getAsJsonArray("items").size());assertTrue(normal.get("items_truncated").getAsBoolean());
   var full=surroundings.toJson(Detail.FULL).getAsJsonArray("block_entities").get(0).getAsJsonObject();
   assertEquals(14,full.getAsJsonArray("items").size());assertEquals(14,full.getAsJsonArray("items").get(13).getAsJsonObject().get("count").getAsInt());
 }
}

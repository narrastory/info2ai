package com.example.info2ai;
import com.example.info2ai.capture.server.InventorySummary;
import com.example.info2ai.model.ItemStackInfo;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class InventorySummaryTest {
 @Test public void occupiedSlotsCountStacksRatherThanItemsAndPreserveNulls() {
   var slots=new ArrayList<ItemStackInfo>(Collections.nCopies(36,null));
   slots.set(0,new ItemStackInfo("minecraft:bread",64,null,0,null,List.of(),null,0));
   slots.set(9,new ItemStackInfo("minecraft:bread",1,null,0,null,List.of(),null,9));
   assertEquals(2,InventorySummary.used(slots));
 }
 @Test public void summaryGroupsIdsRanksCountsAndFallsBackToResourcePath() throws Exception {
   VanillaLocaleFixture.initialize(); var values=List.of(new InventorySummary.Entry("minecraft:bread",3,0),new InventorySummary.Entry("minecraft:bread",2,0),new InventorySummary.Entry("custom:unknown",9,0));
   assertEquals("9 unknown、5 面包",InventorySummary.summary(values));
 }
}

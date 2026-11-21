package com.example.info2ai;
import com.example.info2ai.capture.merge.*;
import com.example.info2ai.http.MockSnapshotFactory;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class FreshRequestTest {
 @Test public void freshRequestsWaitForServerPublicationAndUnloadRejectsPendingWithoutResettingSeq() {
   var store=new SnapshotStore();assertNull(store.requestFresh().join());
   store.publish(MockSnapshotFactory.create());store.enableFresh(true);
   var requested=store.requestFresh();assertFalse(requested.isDone());
   var batch=store.takeFreshRequests();assertEquals(1,batch.size());
   var accepted=store.publishServer(MockSnapshotFactory.create());store.completeFresh(batch,accepted);assertSame(accepted,requested.join());
   var pending=store.requestFresh();long seq=store.sequence();
   store.perfStats().record(1200);store.warning("example");store.clearWorld();
   assertNull(pending.join());assertNull(store.current());assertTrue(store.sequence()>seq);
   assertEquals(0,store.perfStats().snapshot().samples());assertTrue(store.warnings().isEmpty());assertNull(store.requestFresh().join());
 }
 @Test public void takenRequestCannotSucceedAfterEpochInvalidationAndSameNamedWorldReentry() {
   try(var store=new SnapshotStore()) {
     var sample=MockSnapshotFactory.create();store.publishServer(sample);store.enableFresh(true);
     var request=store.requestFresh();var taken=store.takeFreshRequests();long old=store.worldEpoch();
     store.invalidateWorld(old);var rejected=store.publishServer(sample,old);assertNull(rejected);
     store.publishServer(sample,store.worldEpoch());store.enableFresh(true);
     store.completeFresh(taken,rejected);
     assertNull("旧世界在途请求不能成功",request.join());
   }
 }
 @Test public void oldServerCannotTakeReplacementWorldRequests() {
   try(var store=new SnapshotStore()) {
     store.publishServer(MockSnapshotFactory.create());long old=store.worldEpoch();store.invalidateWorld(old);
     store.publishServer(MockSnapshotFactory.create());store.enableFresh(true);var request=store.requestFresh();
     assertTrue(store.takeFreshRequests(old).isEmpty());assertFalse(request.isDone());
     var taken=store.takeFreshRequests(store.worldEpoch());assertEquals(1,taken.size());
     var accepted=store.publishServer(MockSnapshotFactory.create());store.completeFresh(taken,accepted);
     assertSame(accepted,request.join());
   }
 }
 @Test public void previousServerResultCannotCountAsFreshAfterClientUpdates() {
   try(var store=new SnapshotStore()) {
     var previous=store.publishServer(MockSnapshotFactory.create());store.enableFresh(true);
     var requested=store.requestFresh();var taken=store.takeFreshRequests();
     store.publish(MockSnapshotFactory.create());store.completeFresh(taken,previous);
     assertNull(requested.join());
   }
 }
 @Test public void requestStartedInOldEpochCannotEnqueueIntoReplacementWorld() {
   try(var store=new SnapshotStore()) {
     store.publishServer(MockSnapshotFactory.create());store.enableFresh(true);long startEpoch=store.worldEpoch();
     store.invalidateWorld(startEpoch);store.publishServer(MockSnapshotFactory.create());store.enableFresh(true);
     assertNull(store.requestFresh(startEpoch).join());assertTrue(store.takeFreshRequests().isEmpty());
   }
 }
 @Test public void queueIsBoundedAndCancelledTimeoutsNeverReachServerWork() {
   var store=new SnapshotStore();store.publish(MockSnapshotFactory.create());store.enableFresh(true);
   List<FreshRequest> pending=new ArrayList<>();for(int i=0;i<16;i++)pending.add(store.requestFresh());
   assertNull(store.requestFresh().join());pending.getFirst().cancel(false);
   assertEquals(15,store.takeFreshRequests().size());assertTrue(store.takeFreshRequests().isEmpty());store.close();
 }
}

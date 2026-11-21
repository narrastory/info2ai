package com.example.info2ai;

import com.example.info2ai.staticworld.*;
import com.example.info2ai.model.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CachedStaticWorldSourceTest {
    @Test public void outageBackoffSurvivesHeightAndBucketMovementThenRecovers() throws Exception {
        var calls=new AtomicInteger();var online=new AtomicBoolean();
        StaticWorldSource source=new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q){calls.incrementAndGet();return online.get();}
            public StaticWorldInfo getStatic(StaticQuery q){return data(q.x());}
            public String describe(){return "outage-test";}
        };
        try(var cache=new CachedStaticWorldSource(source)) {
            cache.read(query(0,"minecraft:overworld",0,64));await(()->cache.metrics().get("failures").getAsInt()==1);
            for(int i=0;i<5;i++) {
                var view=cache.read(query(0,"minecraft:overworld",128*(i+1),65+i));
                Thread.sleep(35);assertFalse(view.available());
            }
            assertEquals("movement must preserve the first one-second outage delay",1,calls.get());
            assertEquals("static_world_unavailable",cache.read(query(0,"minecraft:overworld",800,70)).error());
            await(()->calls.get()==2);await(()->cache.metrics().get("failures").getAsInt()==2);
            online.set(true);var last=query(0,"minecraft:overworld",900,71);cache.read(last);
            Thread.sleep(150);assertEquals("second outage delay survives movement",2,calls.get());
            await(()->cache.read(last).ready());assertTrue(cache.read(last).available());assertEquals(3,calls.get());
            assertEquals(900,cache.read(last).info().structures().getFirst().x().intValue());
        }
    }

    @Test public void radiusChangesAndClearResetOutageScope() throws Exception {
        var calls=new AtomicInteger();
        StaticWorldSource source=new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q){calls.incrementAndGet();return false;}
            public StaticWorldInfo getStatic(StaticQuery q){throw new AssertionError();}
            public String describe(){return "outage-test";}
        };
        try(var cache=new CachedStaticWorldSource(source)) {
            var q=query(0,"minecraft:overworld",0,64);cache.read(q);await(()->calls.get()==1);
            var changed=new StaticQuery(0,"world","0","1.21.1",q.dimension(),q.generation(),0,64,0,3000,true,q.context());
            cache.read(changed);await(()->calls.get()==2);
            cache.clear();cache.read(changed);await(()->calls.get()==3);
        }
    }
    @Test public void sameKeyReentryAndHeightEpochGenerationChangesFenceOldWork() throws Exception {
        var started=new CountDownLatch(1);var firstRelease=new CountDownLatch(1);
        var secondStarted=new CountDownLatch(1);var secondRelease=new CountDownLatch(1);var calls=new AtomicInteger();
        StaticWorldSource source=new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q){return true;}
            public StaticWorldInfo getStatic(StaticQuery q) throws Exception {
                int call=calls.incrementAndGet();if(call==1){started.countDown();firstRelease.await(3,TimeUnit.SECONDS);}
                else {secondStarted.countDown();secondRelease.await(3,TimeUnit.SECONDS);}return data(call*100);
            }
            public String describe(){return "test";}
        };
        try(var cache=new CachedStaticWorldSource(source)) {
            var original=query(0,"minecraft:overworld",0,64);cache.read(original);assertTrue(started.await(2,TimeUnit.SECONDS));
            cache.read(query(0,"minecraft:overworld",0,72));
            cache.read(query(1,"minecraft:overworld",0,72));
            cache.clear();cache.read(original);firstRelease.countDown();
            assertTrue(secondStarted.await(2,TimeUnit.SECONDS));assertFalse(cache.read(original).ready());
            secondRelease.countDown();await(()->cache.read(original).ready());
            assertEquals(200,cache.read(original).info().structures().getFirst().x().intValue());
            var unknown=query(0,"example:custom",0,64);
            assertFalse(cache.read(unknown).ready());assertFalse(cache.read(unknown).available());
        } finally {firstRelease.countDown();secondRelease.countDown();}
    }

    @Test public void reachableSourceStaysAvailableWhileNewBucketIsNotReady() throws Exception {
        var release=new CountDownLatch(1);
        StaticWorldSource source=new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q){return true;}
            public StaticWorldInfo getStatic(StaticQuery q) throws Exception {if(q.x()>=128)release.await(2,TimeUnit.SECONDS);return data(200);}
            public String describe(){return "test";}
        };
        try(var cache=new CachedStaticWorldSource(source)) {
            var first=query(0,"minecraft:overworld",0,64);await(()->cache.read(first).ready());
            var next=cache.read(query(0,"minecraft:overworld",200,64));
            assertTrue("connection capability survives an uncached current query",next.available());assertFalse(next.ready());
        } finally {release.countDown();}
    }

    static StaticQuery query(long epoch,String dimension,int x,int y) {
        var context=new GenerationHandle(null,null,null,null,null,null,-64,384,0,dimension,true);
        return new StaticQuery(epoch,"world","0","1.21.1",dimension,"minecraft:normal",x,y,0,2000,true,context);
    }
    static StaticWorldInfo data(int x) {
        return new StaticWorldInfo("cubiomes","medium",null,"bucket sample",List.of(
            new StructureInfo("minecraft:village","minecraft:village_savanna",x,0,null,null,null,null,"medium",null,null)),List.of());
    }
    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(5);
        assertTrue("background condition timed out",condition.getAsBoolean());
    }
    @Test public void callerNeverPerformsIoAndConnectionAvailabilityPrecedesReady() throws Exception {
        var fetched=new CountDownLatch(1);var release=new CountDownLatch(1);var thread=new AtomicReference<String>();
        StaticWorldSource source=new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q) {thread.set(Thread.currentThread().getName());return true;}
            public StaticWorldInfo getStatic(StaticQuery q) throws Exception {fetched.countDown();release.await(3,TimeUnit.SECONDS);return data(200);}
            public String describe() {return "test";}
        };
        try(var cache=new CachedStaticWorldSource(source)) {
            var q=query(0,"minecraft:overworld",0,64);
            long start=System.nanoTime();assertFalse(cache.read(q).ready());assertTrue(System.nanoTime()-start<50_000_000);
            assertTrue(fetched.await(2,TimeUnit.SECONDS));
            assertNotEquals(Thread.currentThread().getName(),thread.get());
            assertTrue(cache.read(q).available());assertFalse(cache.read(q).ready());
            release.countDown();await(()->cache.read(q).ready());
            assertEquals(200,cache.read(q).info().structures().getFirst().x().intValue());
            for(int i=0;i<10000;i++)cache.read(q);
            long measured=System.nanoTime(),max=0;
            for(int i=0;i<10000;i++){long before=System.nanoTime();assertTrue(cache.read(q).ready());max=Math.max(max,System.nanoTime()-before);}
            System.out.println("PHASE6 cache_read warm_10000 average_ns="+((System.nanoTime()-measured)/10000)+" max_ns="+max);
        } finally {release.countDown();}
    }
    @Test public void delayedOldDimensionSuccessAndFailureCannotReplaceLatestRequest() throws Exception {
        for(boolean fail:List.of(false,true)) {
            var oldStarted=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
            StaticWorldSource source=new StaticWorldSource() {
                public boolean isAvailable(StaticQuery q) {return true;}
                public StaticWorldInfo getStatic(StaticQuery q) throws Exception {
                    calls.incrementAndGet();
                    if(q.dimension().equals("minecraft:overworld")) {oldStarted.countDown();release.await(2,TimeUnit.SECONDS);
                        if(fail)throw new java.io.IOException("old");return data(11);}
                    return data(22);
                }
                public String describe() {return "test";}
            };
            try(var cache=new CachedStaticWorldSource(source)) {
                cache.read(query(0,"minecraft:overworld",0,64));assertTrue(oldStarted.await(2,TimeUnit.SECONDS));
                var current=query(0,"minecraft:the_nether",0,70);assertFalse(cache.read(current).ready());release.countDown();
                await(()->cache.read(current).ready());
                assertEquals(22,cache.read(current).info().structures().getFirst().x().intValue());
                assertNull(cache.read(current).error());assertEquals(2,calls.get());
            } finally {release.countDown();}
        }
    }
    @Test public void unavailableSourceBacksOffAndUnsupportedDimensionNeverCallsSource() throws Exception {
        var calls=new AtomicInteger();
        StaticWorldSource source=new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q) {calls.incrementAndGet();return false;}
            public StaticWorldInfo getStatic(StaticQuery q) {throw new AssertionError();}
            public String describe() {return "test";}
        };
        try(var cache=new CachedStaticWorldSource(source)) {
            var q=query(0,"minecraft:overworld",0,64);cache.read(q);await(()->calls.get()==1);
            for(int i=0;i<1000;i++)assertFalse(cache.read(q).available());
            Thread.sleep(100);assertEquals(1,calls.get());
            var unsupported=query(0,"example:custom",0,64);
            assertFalse(cache.read(unsupported).available());Thread.sleep(100);assertEquals(1,calls.get());
        }
    }

    @Test public void contextArrivalStartsWorkEvenWhenCacheKeyIsUnchanged() throws Exception {
        StaticWorldSource source=new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q){return true;}
            public StaticWorldInfo getStatic(StaticQuery q){return data(100);}
            public String describe(){return "test";}
        };
        try(var cache=new CachedStaticWorldSource(source)) {
            var valid=query(0,"minecraft:overworld",0,64);
            var missing=new StaticQuery(0,"world","0","1.21.1",valid.dimension(),valid.generation(),0,64,0,2000,true,null);
            assertEquals(valid.key(),missing.key());
            assertFalse(cache.read(missing).available());
            await(()->cache.read(valid).ready());
        }
    }

    @Test public void retriesBudgetTruncatedEmptyResultsWithoutDroppingTheSnapshotOrLoopingForever() throws Exception {
        var calls = new AtomicInteger();
        var secondStarted = new CountDownLatch(1);
        var releaseSecond = new CountDownLatch(1);
        StaticWorldSource source = new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q) { return true; }
            public String describe() { return "builtin"; }
            public StaticWorldInfo getStatic(StaticQuery q) throws InterruptedException {
                int attempt = calls.incrementAndGet();
                if (attempt == 2) {
                    secondStarted.countDown();
                    releaseSecond.await();
                    return data(100);
                }
                return new StaticWorldInfo("builtin", "high", null, null, List.of(), List.of(), null,
                    com.example.info2ai.events.EventData.of("truncated_by_budget", true), null);
            }
        };
        try (var cache = new CachedStaticWorldSource(source)) {
            var first = query(0, "minecraft:overworld", 0, 64);
            await(() -> cache.read(first).ready());
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            assertTrue(cache.read(first).ready());
            assertTrue(cache.read(first).info().structures().isEmpty());
            releaseSecond.countDown();
            await(() -> !cache.read(first).info().structures().isEmpty());
            assertEquals(2, calls.get());

            var alwaysEmpty = query(0, "minecraft:overworld", 256, 64);
            cache.read(alwaysEmpty);
            await(() -> calls.get() >= 5);
            Thread.sleep(650);
            assertEquals("at most two retries for the same cached query", 5, calls.get());
            assertTrue(cache.read(alwaysEmpty).ready());
        } finally {
            releaseSecond.countDown();
        }
    }

    @Test public void oneDimensionFailureDoesNotBackOffAnotherDimension() throws Exception {
        var netherStarted = new CountDownLatch(1);
        StaticWorldSource source = new StaticWorldSource() {
            public boolean isAvailable(StaticQuery q) { return true; }
            public String describe() { return "builtin"; }
            public StaticWorldInfo getStatic(StaticQuery q) {
                if (q.shortDimension().equals("overworld")) throw new IllegalStateException("broken generator");
                netherStarted.countDown();
                return data(100);
            }
        };
        try (var cache = new CachedStaticWorldSource(source)) {
            var overworld = query(0, "minecraft:overworld", 0, 64);
            await(() -> "static_world_unavailable".equals(cache.read(overworld).error()));
            var nether = query(0, "minecraft:the_nether", 0, 64);
            cache.read(nether);
            assertTrue("the Nether calculation must start without the Overworld's delay", netherStarted.await(500, TimeUnit.MILLISECONDS));
            await(() -> cache.read(nether).ready());
        }
    }
}

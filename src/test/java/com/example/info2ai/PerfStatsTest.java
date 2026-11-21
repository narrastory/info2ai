package com.example.info2ai;

import com.example.info2ai.capture.merge.PerfStats;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class PerfStatsTest {
    @Test public void rollingWindowEvictsOldMaximumAndKeepsOnly300Samples() {
        var stats=new PerfStats();stats.record(9000);for(int i=0;i<299;i++)stats.record(1000);
        assertEquals(300,stats.snapshot().samples());assertEquals(9,stats.snapshot().maxMs(),0);
        stats.record(1000);assertEquals(1,stats.snapshot().avgMs(),0);assertEquals(1,stats.snapshot().maxMs(),0);
        stats.reset();assertEquals(0,stats.snapshot().samples());
    }
    @Test public void slowMetricReaderDoesNotBlockRecord() throws Exception {
        var stats=new PerfStats();stats.record(1000);
        whileReaderHoldsPublicMonitor(stats,()->stats.record(9000));
        assertSample(stats.snapshot(),2,5,9);
    }
    @Test public void slowMetricReaderDoesNotBlockReset() throws Exception {
        var stats=new PerfStats();stats.record(9000);
        whileReaderHoldsPublicMonitor(stats,stats::reset);
        assertSample(stats.snapshot(),0,0,0);
    }
    /** 重现JFR中的公开monitor所有权，明确在释放reader前要求写入完成；不是毫秒预算测试。 */
    private static void whileReaderHoldsPublicMonitor(PerfStats stats,Runnable operation) throws Exception {
        CountDownLatch reading=new CountDownLatch(1),release=new CountDownLatch(1),completed=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread reader=Thread.ofVirtual().name("test-http-metric-reader").start(()-> {
            synchronized(stats) {
                try {
                    stats.snapshot();reading.countDown();release.await();
                } catch(Throwable error) {failure.set(error);reading.countDown();}
            }
        });
        Thread producer=null;
        try {
            assertTrue("reader进入受控调用边界",reading.await(5,TimeUnit.SECONDS));
            assertNull(failure.get());
            producer=Thread.ofPlatform().name("test-game-metric-producer").start(()-> {
                try {operation.run();}catch(Throwable error) {failure.set(error);}finally {completed.countDown();}
            });
            assertTrue("reader仍被latch挂起时，生产者应独立完成",completed.await(2,TimeUnit.SECONDS));
            assertNull(failure.get());
        } finally {
            release.countDown();reader.join(5000);
            if(producer!=null)producer.join(5000);
        }
    }
    @Test public void repeatedMaximaRemainUntilTheirLastRollingSlotIsEvicted() {
        var stats=new PerfStats();stats.record(9000);stats.record(9000);
        for(int i=0;i<298;i++)stats.record(1000);
        assertSample(stats.snapshot(),300,1.0533333333333332,9);
        stats.record(1000);assertSample(stats.snapshot(),300,1.0266666666666666,9);
        stats.record(1000);assertSample(stats.snapshot(),300,1,1);
    }
    @Test public void snapshotsAreImmutableCompletedViewsAcrossResetAndNegativeClamp() {
        var stats=new PerfStats();assertSample(stats.snapshot(),0,0,0);
        stats.record(1000);var before=stats.snapshot();
        stats.record(9000);var full=stats.snapshot();
        assertSample(before,1,1,1);assertSample(full,2,5,9);
        stats.reset();assertSample(stats.snapshot(),0,0,0);assertSample(full,2,5,9);
        stats.record(-123);assertSample(stats.snapshot(),1,0,0);
        stats.record(2000);assertSample(stats.snapshot(),2,1,2);
    }
    @Test public void twoProducersKeepBothCompletedSamples() throws Exception {
        var stats=new PerfStats();CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1),done=new CountDownLatch(2);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        for(long value:new long[]{1000,3000})Thread.ofVirtual().start(()-> {
            ready.countDown();
            try {start.await();stats.record(value);}catch(Throwable error) {failure.set(error);}finally {done.countDown();}
        });
        try {assertTrue(ready.await(5,TimeUnit.SECONDS));}finally {start.countDown();}
        assertTrue(done.await(5,TimeUnit.SECONDS));assertNull(failure.get());
        assertSample(stats.snapshot(),2,2,3);
    }
    private static void assertSample(PerfStats.Sample sample,int count,double avg,double max) {
        assertEquals(count,sample.samples());assertEquals(avg,sample.avgMs(),1e-12);assertEquals(max,sample.maxMs(),0);
    }
}

package com.example.info2ai;
import com.example.info2ai.capture.server.CaptureLogBudget;
import com.example.info2ai.capture.merge.CaptureDiagnostics;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptureDiagnosticsTest {
    @Test public void failuresShareOneBudgetKeepCauseAndRecoverIndependently() {
        var epoch=new AtomicLong(1);var clock=new AtomicLong();var logged=new ArrayList<Throwable>();
        var diagnostics=new CaptureDiagnostics(epoch::get,new CaptureLogBudget(clock::get),(code,cause)->logged.add(cause));
        var first=new IllegalStateException("framebuffer");var second=new IllegalArgumentException("HUD");
        diagnostics.failure(1,"screenshot_failed",first);
        assertTrue(diagnostics.warnings().contains("screenshot_failed"));assertTrue(logged.isEmpty());
        diagnostics.flush();assertEquals(List.of(first),logged);
        for(int i=0;i<20;i++)diagnostics.failure(1,"capture_hud_failed",second);
        diagnostics.flush();assertEquals(1,logged.size());
        diagnostics.success(1,"screenshot_failed");assertFalse(diagnostics.warnings().contains("screenshot_failed"));
        assertTrue(diagnostics.warnings().contains("capture_hud_failed"));
        clock.set(1_000_000_000L);diagnostics.flush();assertEquals(List.of(first,second),logged);
        diagnostics.success(1,"capture_hud_failed");assertTrue(diagnostics.warnings().isEmpty());
    }
    @Test public void lateFailureAndRecoveryCannotPolluteReplacementEpoch() {
        var epoch=new AtomicLong(1);var clock=new AtomicLong();var logged=new ArrayList<Throwable>();
        var diagnostics=new CaptureDiagnostics(epoch::get,new CaptureLogBudget(clock::get),(code,cause)->logged.add(cause));
        diagnostics.failure(1,"capture_failed",new IllegalStateException("old"));
        epoch.set(2);assertTrue(diagnostics.warnings().isEmpty());diagnostics.flush();assertTrue(logged.isEmpty());
        var current=new IllegalStateException("new");diagnostics.failure(2,"capture_failed",current);
        diagnostics.failure(1,"old_warning",new IllegalStateException("late"));diagnostics.success(1,"capture_failed");
        assertEquals(List.of("capture_failed"),diagnostics.warnings());
        clock.set(1_000_000_000L);diagnostics.flush();assertEquals(List.of(current),logged);
    }
    @Test public void enqueueDoesNotWaitForSlowLoggingSink() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var budget=new CaptureLogBudget(()->0L);
        budget.offer(CaptureLogBudget.Priority.ERROR,()->{ entered.countDown();try { release.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } });
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var flushing=executor.submit(budget::flush);
            try {
                assertTrue(entered.await(1,TimeUnit.SECONDS));
                executor.submit(()->budget.offer(CaptureLogBudget.Priority.ERROR,()->{})).get(200,TimeUnit.MILLISECONDS);
            } finally { release.countDown();flushing.get(1,TimeUnit.SECONDS); }
        }
    }
}

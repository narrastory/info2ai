package com.example.info2ai;

import com.example.info2ai.api.*;
import com.example.info2ai.screenshot.CaptureCoordinator;
import com.example.info2ai.http.ApiException;
import com.example.info2ai.model.Facts;
import com.google.gson.JsonObject;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class ScreenshotTest {
    @Test public void resultOwnsBytesAndMetadataEvenAfterCallerMutation() {
        byte[] bytes = {1,2,3};
        var context = new JsonObject(); context.addProperty("world_id", "old-world");
        var result = new ScreenshotResult(bytes, 41, 1234, Facts.fromJson(context), false);
        bytes[0] = 9; result.bytes()[1] = 9; context.addProperty("world_id", "new-world");
        assertArrayEquals(new byte[]{1,2,3}, result.bytes());
        assertEquals(41, result.seq()); assertEquals(1234, result.capturedAt());
        assertEquals("old-world", result.context().toJson().get("world_id").getAsString());
    }

    @Test public void expiredQueuedCaptureNeverRunsAndDoesNotOccupyNextAdmission() throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var clock = new AtomicLong(0);
        var calls = new AtomicInteger();
        var coordinator = new CaptureCoordinator(queue::add, () -> false, clock::get, 200);
        ApiException error = assertThrows(ApiException.class, () -> coordinator.capture(() -> true, () -> {
            calls.incrementAndGet(); return result();
        }));
        assertEquals("render_timeout", error.code());
        queue.remove().run(); assertEquals(0, calls.get());
        clock.set(1_000_000_000L);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var next = executor.submit(() -> coordinator.capture(() -> true, ScreenshotTest::result));
            queue.poll(1, TimeUnit.SECONDS).run(); assertEquals(41, next.get().seq());
        }
    }

    @Test public void invalidatedWorldNeverReadsFramebufferAndRateIsOnePerSecond() {
        var valid = new AtomicBoolean(false);
        var coordinator = new CaptureCoordinator(Runnable::run, () -> true, () -> 0L, 2000);
        assertEquals("no_client", assertThrows(ApiException.class,
            () -> coordinator.capture(valid::get, () -> { fail("old callback read new framebuffer"); return result(); })).code());
        valid.set(true);
        assertEquals(41, coordinator.capture(valid::get, ScreenshotTest::result).seq());
        assertEquals(429, assertThrows(ApiException.class,
            () -> coordinator.capture(valid::get, ScreenshotTest::result)).status());
    }
    @Test public void queuedCallbackFromDisconnectedWorldCannotCaptureReplacementWorld() throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var generation = new AtomicInteger(1);
        var coordinator = new CaptureCoordinator(queue::add, () -> false, System::nanoTime, 2000);
        var captures = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var response = executor.submit(() -> coordinator.capture(() -> generation.get()==1, () -> {
                captures.incrementAndGet(); return result();
            }));
            var callback = queue.poll(1, TimeUnit.SECONDS); assertNotNull(callback);
            generation.set(2); callback.run();
            var failed = assertThrows(ExecutionException.class, () -> response.get(1, TimeUnit.SECONDS));
            assertEquals("no_client", ((ApiException)failed.getCause()).code()); assertEquals(0, captures.get());
        }
    }

    @Test public void renderFailurePreservesOriginalCause() {
        var coordinator=new CaptureCoordinator(Runnable::run,()->true,System::nanoTime,2000);
        var cause=new IllegalStateException("framebuffer unavailable");
        var failure=assertThrows(ApiException.class,()->coordinator.capture(()->true,()->{ throw cause; }));
        assertEquals("screenshot_failed",failure.code());assertSame(cause,failure.getCause());
    }

    @Test public void realFailureLogsOriginalCauseOnceWhileSuccessRecoversAndNormalExitIsNotFailure() {
        var clock=new AtomicLong();var epoch=new AtomicLong(1);var logged=new java.util.ArrayList<Throwable>();
        var diagnostics=new com.example.info2ai.capture.merge.CaptureDiagnostics(epoch::get,
            new com.example.info2ai.capture.server.CaptureLogBudget(clock::get),(code,cause)->logged.add(cause));
        var coordinator=new CaptureCoordinator(Runnable::run,()->true,clock::get,2000);
        var cause=new java.io.IOException("PNG encode");
        var failure=assertThrows(ApiException.class,()->coordinator.capture(()->true,()->{
            throw new ApiException(503,"screenshot_failed",null,cause);
        },diagnostics,1));
        assertSame(cause,failure.getCause());assertTrue(diagnostics.warnings().contains("screenshot_failed"));
        diagnostics.flush();assertEquals(java.util.List.of(cause),logged);
        clock.set(1_000_000_000L);coordinator.capture(()->true,ScreenshotTest::result,diagnostics,1);
        assertTrue(diagnostics.warnings().isEmpty());
        assertEquals("no_client",assertThrows(ApiException.class,()->coordinator.capture(()->false,ScreenshotTest::result,diagnostics,1)).code());
        diagnostics.flush();assertEquals(1,logged.size());assertTrue(diagnostics.warnings().isEmpty());
    }

    @Test public void startedPixelsFailureAfterHttpTimeoutStillWarnsAndLogsOriginalCauseExactlyOnce() throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var clock=new AtomicLong();var epoch=new AtomicLong(1);var logged=new java.util.ArrayList<Throwable>();
        var diagnostics=new com.example.info2ai.capture.merge.CaptureDiagnostics(epoch::get,
            new com.example.info2ai.capture.server.CaptureLogBudget(clock::get),(code,cause)->logged.add(cause));
        var cause=new java.io.IOException("late PNG encode failure");
        try(var threads=Executors.newVirtualThreadPerTaskExecutor()) {
            Executor render=task->threads.execute(()->{ try { task.run(); } finally { finished.countDown(); } });
            var coordinator=new CaptureCoordinator(render,()->false,System::nanoTime,200);
            var request=threads.submit(()->coordinator.capture(()->true,()->{
                started.countDown();
                try { release.await(); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw new RuntimeException(interrupted); }
                throw new ApiException(503,"screenshot_failed",null,cause);
            },diagnostics,1));
            try {
                assertTrue(started.await(1,TimeUnit.SECONDS));
                var failure=assertThrows(ExecutionException.class,()->request.get(1,TimeUnit.SECONDS));
                assertEquals("render_timeout",((ApiException)failure.getCause()).code());
                assertTrue("普通超时不应生成硬件故障",diagnostics.warnings().isEmpty());
                release.countDown();assertTrue(finished.await(1,TimeUnit.SECONDS));
                threads.submit(diagnostics::flush).get(1,TimeUnit.SECONDS);
                assertTrue(diagnostics.warnings().contains("screenshot_failed"));
                assertEquals(java.util.List.of(cause),logged);
                clock.set(1_000_000_000L);threads.submit(diagnostics::flush).get(1,TimeUnit.SECONDS);
                assertEquals("同一次真实失败只输出一次",1,logged.size());
            } finally { release.countDown(); }
        }
    }

    @Test public void waiterDoesNotReportAgainAfterWorkAlreadyLoggedFailure() {
        var clock=new AtomicLong();var logged=new java.util.ArrayList<Throwable>();
        var diagnostics=new com.example.info2ai.capture.merge.CaptureDiagnostics(()->1L,
            new com.example.info2ai.capture.server.CaptureLogBudget(clock::get),(code,cause)->logged.add(cause));
        Executor render=task->{ task.run();diagnostics.flush(); };
        var coordinator=new CaptureCoordinator(render,()->false,System::nanoTime,2000);
        var cause=new IllegalStateException("readback failure");
        var failure=assertThrows(ApiException.class,()->coordinator.capture(()->true,()->{ throw cause; },diagnostics,1));
        assertSame(cause,failure.getCause());assertEquals(java.util.List.of(cause),logged);
        clock.set(1_000_000_000L);diagnostics.flush();assertEquals(1,logged.size());
    }

    private static ScreenshotResult result() { return new ScreenshotResult(new byte[]{1}, 41, 1234, null, false); }
}

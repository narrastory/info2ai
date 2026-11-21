package com.example.info2ai.screenshot;
import com.example.info2ai.api.ScreenshotResult;
import com.example.info2ai.http.ApiException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** 队列超时后取消工作，执行前后均检查世界身份。所有等待仅发生在请求线程。 */
public final class CaptureCoordinator {
    private final Executor render;
    private final BooleanSupplier onRenderThread;
    private final LongSupplier nanoTime;
    private final long timeoutMs;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicLong lastStart = new AtomicLong(Long.MIN_VALUE);

    public CaptureCoordinator(Executor render, BooleanSupplier onRenderThread, LongSupplier nanoTime, long timeoutMs) {
        this.render = render; this.onRenderThread = onRenderThread; this.nanoTime = nanoTime; this.timeoutMs = timeoutMs;
    }
    public ScreenshotResult capture(BooleanSupplier valid, Supplier<ScreenshotResult> pixels) {
        return capture(valid,pixels,null,0);
    }
    public ScreenshotResult capture(BooleanSupplier valid,Supplier<ScreenshotResult> pixels,
            com.example.info2ai.capture.merge.CaptureDiagnostics diagnostics,long epoch) {
        if (!valid.getAsBoolean()) throw new ApiException(503, "no_client");
        if (!busy.compareAndSet(false, true)) throw new ApiException(429, "rate_limited");
        long now = nanoTime.getAsLong();
        long previous = lastStart.get();
        if (previous != Long.MIN_VALUE && now - previous < 1_000_000_000L) {
            busy.set(false); throw new ApiException(429, "rate_limited");
        }
        lastStart.set(now);
        long deadline = now + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        var result = new CompletableFuture<ScreenshotResult>();
        Runnable work = () -> {
            if (result.isDone()) return;
            if (nanoTime.getAsLong() >= deadline) { result.completeExceptionally(new ApiException(503, "render_timeout")); return; }
            if (!valid.getAsBoolean()) { result.completeExceptionally(new ApiException(503, "no_client")); return; }
            try {
                ScreenshotResult captured = pixels.get();
                if (!valid.getAsBoolean()) throw new ApiException(503, "no_client");
                if (nanoTime.getAsLong() >= deadline) throw new ApiException(503, "render_timeout");
                if(diagnostics!=null)diagnostics.success(epoch,"screenshot_failed");
                result.complete(captured);
            } catch (RuntimeException | LinkageError failure) {
                // 即使请求方已超时并取消 future，已启动的真实失败仍必须独立诊断。
                result.completeExceptionally(captureFailure(failure,valid,diagnostics,epoch));
            }
        };
        try {
            if (onRenderThread.getAsBoolean()) work.run(); else render.execute(work);
            ScreenshotResult captured=result.get(timeoutMs, TimeUnit.MILLISECONDS);
            if(!valid.getAsBoolean())throw new ApiException(503,"no_client");
            return captured;
        } catch (TimeoutException timeout) {
            throw new ApiException(503, "render_timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new ApiException(503, "render_timeout");
        } catch (ExecutionException failed) {
            // work 已负责诊断；等待方只分类/传播，不能重复记录同一次失败。
            throw normalizeFailure(failed.getCause(),valid);
        } catch (RuntimeException failure) {
            throw captureFailure(failure,valid,diagnostics,epoch);
        } finally {
            result.cancel(false); busy.set(false);
        }
    }
    private static ApiException captureFailure(Throwable failure,BooleanSupplier valid,
            com.example.info2ai.capture.merge.CaptureDiagnostics diagnostics,long epoch) {
        ApiException normalized=normalizeFailure(failure,valid);
        if(diagnostics!=null && normalized.code().equals("screenshot_failed"))
            diagnostics.failure(epoch,"screenshot_failed",normalized.getCause());
        return normalized;
    }
    private static ApiException normalizeFailure(Throwable failure,BooleanSupplier valid) {
        if(failure instanceof ApiException api && java.util.Set.of("no_client","paused","render_timeout","rate_limited").contains(api.code()))return api;
        if(!valid.getAsBoolean())return new ApiException(503,"no_client");
        Throwable cause=failure instanceof ApiException && failure.getCause()!=null?failure.getCause():failure;
        return new ApiException(503,"screenshot_failed",null,cause);
    }
}

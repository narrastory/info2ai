package com.example.info2ai.capture.merge;

import java.util.concurrent.*;

/** 请求携带进入队列时的世界代数；只有 store 可以完成具体的服务端发布结果。 */
public final class FreshRequest {
    private final long epoch;
    private final long afterSeq;
    private final CompletableFuture<SnapshotStore.ServerPublication> future = new CompletableFuture<>();
    FreshRequest(long epoch,long afterSeq) { this.epoch=epoch;this.afterSeq=afterSeq; }
    public long epoch() { return epoch; }
    public long afterSeq() { return afterSeq; }
    void finish(SnapshotStore.ServerPublication result) { future.complete(result); }
    public SnapshotStore.ServerPublication get(long timeout,TimeUnit unit) throws InterruptedException,ExecutionException,TimeoutException {
        return future.get(timeout,unit);
    }
    public SnapshotStore.ServerPublication join() { return future.join(); }
    public boolean isDone() { return future.isDone(); }
    public boolean cancel(boolean interrupt) { return future.cancel(interrupt); }
}

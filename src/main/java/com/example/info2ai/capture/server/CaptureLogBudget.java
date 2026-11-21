package com.example.info2ai.capture.server;

import java.util.function.LongSupplier;

/** All capture messages compete for one monotonic one-second budget. */
public final class CaptureLogBudget {
    public enum Priority { DEBUG, ERROR }
    private final LongSupplier clock;
    private long lastEmission;
    private boolean emitted;
    private record Pending(Priority priority,Runnable message) { }
    private final java.util.concurrent.atomic.AtomicReference<Pending> pending=new java.util.concurrent.atomic.AtomicReference<>();

    public CaptureLogBudget(LongSupplier clock) {
        this.clock = clock;
    }

    /** 不取得 flush 的锁，即使后台 sink 很慢，游戏线程也只做有界 CAS。 */
    public void offer(Priority priority, Runnable message) {
        pending.updateAndGet(before -> before==null || priority.compareTo(before.priority())>=0 ? new Pending(priority,message) : before);
    }

    public synchronized void flush() {
        long now = clock.getAsLong();
        if (pending.get() == null || (emitted && now - lastEmission < 1_000_000_000L)) return;
        Pending next = pending.getAndSet(null);
        if(next==null)return;
        lastEmission = now;
        emitted = true;
        next.message().run();
    }

    /** World changes discard messages, but cannot reset the process-wide frequency budget. */
    public void discardPending() {
        pending.set(null);
    }
}

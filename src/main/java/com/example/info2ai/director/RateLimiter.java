package com.example.info2ai.director;

import com.example.info2ai.api.DirectorRequests.RateStatus;
import com.example.info2ai.api.DirectorRequests.RunStatus;

import java.util.ArrayDeque;
import java.util.Deque;

/** 每次调用使用同一条单调时钟。检查与事后记账由服务端串行执行。 */
public final class RateLimiter {
    private static final long WINDOW_MS = 60_000;
    private int limit;
    private final Deque<Long> timestamps = new ArrayDeque<>();

    public RateLimiter(int limit) {
        limit(limit);
    }

    public synchronized void limit(int limit) {
        if (limit < 1) throw new IllegalArgumentException("额度必须大于零");
        this.limit = limit;
    }

    public synchronized boolean tryAcquire(long nowMs) {
        expire(nowMs);
        if (timestamps.size() >= limit) return false;
        timestamps.addLast(nowMs);
        return true;
    }

    public void record(RunStatus status, long nowMs) {
        switch (status) {
            case EXECUTED, ROLLED_BACK, FAILED, TIMEOUT -> tryAcquire(nowMs);
            case PARSE_ERROR, REJECTED -> { }
        }
    }

    public synchronized RateStatus status(long nowMs) {
        expire(nowMs);
        int reset = timestamps.isEmpty() ? 0 : (int) Math.ceilDiv(WINDOW_MS - (nowMs - timestamps.getFirst()), 1000);
        return new RateStatus(Math.max(0, limit - timestamps.size()), reset);
    }

    private void expire(long nowMs) {
        while (!timestamps.isEmpty() && nowMs - timestamps.getFirst() >= WINDOW_MS) {
            timestamps.removeFirst();
        }
    }
}

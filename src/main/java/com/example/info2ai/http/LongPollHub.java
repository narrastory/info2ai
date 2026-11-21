package com.example.info2ai.http;

import java.util.*;
import java.util.concurrent.*;

public final class LongPollHub {
    private final Set<CompletableFuture<Void>> waiters=new HashSet<>();
    public synchronized CompletableFuture<Void> register(long since,long waitMs) {
        if(waiters.size()>=16)throw new RejectedExecutionException("too_many_waiters");
        CompletableFuture<Void> future=new CompletableFuture<>();
        waiters.add(future);
        return future;
    }
    public synchronized void remove(CompletableFuture<Void> waiter) { waiters.remove(waiter); }
    public synchronized void signal() { for(var waiter:waiters)waiter.complete(null); }
    public synchronized void closeAll() { signal();waiters.clear(); }
    public synchronized int waiterCount() { return waiters.size(); }
}

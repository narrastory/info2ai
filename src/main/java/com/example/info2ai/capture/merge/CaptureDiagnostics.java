package com.example.info2ai.capture.merge;

import com.example.info2ai.capture.server.CaptureLogBudget;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

/** 游戏线程只写有界原子状态/日志槽；日志 sink 只由后台 flush 调用。 */
public final class CaptureDiagnostics {
    private record Status(long epoch,Set<String> warnings) { }
    private final LongSupplier epoch;
    private final CaptureLogBudget budget;
    private final BiConsumer<String,Throwable> logger;
    private final AtomicReference<Status> status=new AtomicReference<>(new Status(-1,Set.of()));
    public CaptureDiagnostics(LongSupplier epoch,CaptureLogBudget budget,BiConsumer<String,Throwable> logger) {
        this.epoch=epoch;this.budget=budget;this.logger=logger;
    }
    public void failure(long expectedEpoch,String code,Throwable cause) {
        if(!change(expectedEpoch,code,true))return;
        budget.offer(CaptureLogBudget.Priority.ERROR,()->{
            if(epoch.getAsLong()==expectedEpoch)logger.accept(code,cause);
        });
    }
    public void unavailable(long expectedEpoch,String code) { change(expectedEpoch,code,true); }
    public void success(long expectedEpoch,String code) { change(expectedEpoch,code,false); }
    private boolean change(long expectedEpoch,String code,boolean failed) {
        while(epoch.getAsLong()==expectedEpoch) {
            Status before=status.get();
            if(before.epoch()==expectedEpoch && before.warnings().contains(code)==failed)return true;
            if(before.epoch()!=expectedEpoch && !failed)return true;
            Set<String> next=new HashSet<>(before.epoch()==expectedEpoch?before.warnings():Set.of());
            if(failed) { if(next.size()<32)next.add(code); } else next.remove(code);
            if(status.compareAndSet(before,new Status(expectedEpoch,Set.copyOf(next))))return true;
        }
        return false;
    }
    public List<String> warnings() {
        Status current=status.get();
        return current.epoch()==epoch.getAsLong()?current.warnings().stream().sorted().toList():List.of();
    }
    public void flush() { budget.flush(); }
}

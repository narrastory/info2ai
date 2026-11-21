package com.example.info2ai.capture.server;

import org.slf4j.LoggerFactory;

/** 所有采集诊断共用一个原子日志槽，后台统一按每秒一条预算输出。 */
public final class CaptureLog {
    private static final CaptureLogBudget BUDGET = new CaptureLogBudget(System::nanoTime);
    private CaptureLog() { }

    public static void debug(String message) {
        debug(message,()->true);
    }
    public static void debug(String message,java.util.function.BooleanSupplier valid) {
        BUDGET.offer(CaptureLogBudget.Priority.DEBUG, () -> {
            if(valid.getAsBoolean())LoggerFactory.getLogger("info2ai.capture").debug(message);
        });
    }

    public static void error(String message, Throwable failure) {
        BUDGET.offer(CaptureLogBudget.Priority.ERROR, () -> LoggerFactory.getLogger("info2ai.capture").error(message, failure));
    }

    public static com.example.info2ai.capture.merge.CaptureDiagnostics diagnostics(java.util.function.LongSupplier epoch) {
        return new com.example.info2ai.capture.merge.CaptureDiagnostics(epoch,BUDGET,
            (code,cause)->LoggerFactory.getLogger("info2ai.capture").error("采集失败: "+code,cause));
    }

    /** 仅后台 publisher 调用；server/client 生产者只 offer。 */
    public static void flush() {
        BUDGET.flush();
    }

    public static void discardPending() {
        BUDGET.discardPending();
    }
}

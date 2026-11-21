package com.example.info2ai.api;

import com.example.info2ai.api.DirectorRequests.*;
import com.example.info2ai.http.ApiException;

import java.util.List;

/** 导演 HTTP 端点与服务端执行之间的接口。 */
public interface DirectorPort {
    DirectorPort NONE = new DirectorPort() {
        @Override public boolean available() { return false; }
        @Override public RunResult run(RunRequest request) { throw unavailable(); }
        @Override public CheckResult check(String command) { throw unavailable(); }
        @Override public UndoResult undo(UndoRequest request) { throw unavailable(); }
        @Override public HelpResult help(String commandPath, String topic) { throw unavailable(); }
        @Override public LookupResult lookup(String kind, String query, int limit) { throw unavailable(); }
        @Override public List<ActionSummary> actions(int limit) { return List.of(); }
        @Override public StatusResult status() { throw unavailable(); }
        @Override public void enabled(boolean on) { throw unavailable(); }

        private ApiException unavailable() {
            return new ApiException(503, "not_in_world");
        }
    };

    boolean available();
    RunResult run(RunRequest request);
    CheckResult check(String command);
    UndoResult undo(UndoRequest request);
    HelpResult help(String commandPath, String topic);
    LookupResult lookup(String kind, String query, int limit);
    List<ActionSummary> actions(int limit);
    StatusResult status();
    void enabled(boolean on);
    default com.google.gson.JsonObject selftest() { throw new ApiException(503, "not_in_world"); }
    default com.example.info2ai.capture.merge.PerfStats.Sample executionPerf() { return null; }
}

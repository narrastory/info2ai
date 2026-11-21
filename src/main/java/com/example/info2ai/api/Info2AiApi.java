package com.example.info2ai.api;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.http.*;
import com.example.info2ai.model.Snapshot;
import java.io.IOException;

public final class Info2AiApi {
    private static volatile SnapshotStore store;
    private static volatile ClientBridge client = ClientBridge.NONE;
    private static volatile DirectorPort director = DirectorPort.NONE;
    private static volatile boolean mock;
    private static volatile com.example.info2ai.history.SourceRuntime source;
    public static boolean realCapture() {var current=source;return current!=null&&current.live();}
    public static ClientBridge client() { return client; }
    public static void client(ClientBridge bridge) { client = store!=null&&!realCapture() ? ClientBridge.NONE : bridge; }
    public static DirectorPort director() { return director; }
    public static void director(DirectorPort port) { director = java.util.Objects.requireNonNull(port); }
    public static boolean mock() { return mock; }
    private static volatile HttpServerHost host;
    private Info2AiApi() { }
    public static synchronized void start(Info2AiConfig config) throws IOException {
        if(isRunning())return;
        SnapshotStore next=new SnapshotStore();
        source=new com.example.info2ai.history.SourceRuntime(config,next);mock=source.mock();
        try { host=HttpServerHost.start(config,next);store=next; }
        catch(IOException | RuntimeException failure) { source.close();source=null;next.close();throw failure; }
    }
    public static Snapshot getSnapshot() { SnapshotStore current=store;return current==null?null:current.current(); }
    public static boolean isRunning() { HttpServerHost current=host;return current!=null && current.isRunning(); }
    public static int actualPort() { HttpServerHost current=host;return current==null?-1:current.actualPort(); }
    public static SnapshotStore store() { return store; }
    public static synchronized void stop() {
        if(host!=null)host.close();
        if(source!=null)source.close();
        if(store!=null)store.close();
        source=null;
        host=null;store=null;client=ClientBridge.NONE;director=DirectorPort.NONE;
    }
}

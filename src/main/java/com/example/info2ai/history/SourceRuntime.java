package com.example.info2ai.history;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.http.MockSnapshotFactory;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;

/** Fixed startup selection shared by Fabric and the pure-JVM launchers. */
public final class SourceRuntime implements AutoCloseable {
    private ReplaySnapshotSource replay;
    private boolean live,mock;
    public boolean live() {return live;}
    public boolean mock() {return mock;}
    public SourceRuntime(Info2AiConfig config,SnapshotStore store) {
        String replayPath=config.replayPath(),recordPath=config.recordPath();
        if(replayPath!=null) {
            if(recordPath!=null||config.mock()) {
                store.replayMode();store.replayUnavailable();store.warning("source_mode_conflict");
                LoggerFactory.getLogger("info2ai.source").error("Replay cannot be combined with recording or Mock");return;
            }
            try {replay=new ReplaySnapshotSource(Path.of(replayPath),store);replay.start();}
            catch(RuntimeException failure) {
                if(!store.replay())store.replayMode();store.replayUnavailable();
                LoggerFactory.getLogger("info2ai.source").error("Replay startup failed ({})",failure.getClass().getSimpleName());
            }
            return;
        }
        live=!config.mock();mock=config.mock();
        if(live)try {
            store.staticSource(new com.example.info2ai.staticworld.CachedStaticWorldSource(new com.example.info2ai.staticworld.BuiltinStaticSource()));
        } catch(RuntimeException|LinkageError failure) {
            store.warning("static_source_init_failed");LoggerFactory.getLogger("info2ai.source").error("Static source startup failed ({})",failure.getClass().getSimpleName());
        }
        try {
            if(live||recordPath!=null)new HistoryStore(live?config.directory().resolve("info2ai/history"):null,store,
                recordPath==null?null:Path.of(recordPath),8192);
        } catch(RuntimeException failure) {
            store.warning("history_init_failed");LoggerFactory.getLogger("info2ai.source").error("History startup failed ({})",failure.getClass().getSimpleName());
        }
        if(mock) {store.sourceStatus("mock");store.publish(MockSnapshotFactory.create());store.emit("world_loaded","info","进入 MOCK 草甸世界");}
    }
    @Override public void close() {if(replay!=null)replay.close();}
}

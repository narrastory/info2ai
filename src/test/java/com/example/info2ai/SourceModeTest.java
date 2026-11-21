package com.example.info2ai;

import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.config.Info2AiConfig;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceModeTest {
    @Test public void conflictingStartupModesExposeUnavailableWithoutLiveOrMockFallback() throws Exception {
        String replay=System.getProperty("info2ai.replay"),record=System.getProperty("info2ai.record");
        Path configDir=PersistentHistoryTest.directory("mode-conflict-");
        Files.writeString(configDir.resolve("info2ai.json"),"{\"http\":{\"port\":0}}");
        try {
            System.setProperty("info2ai.replay",configDir.resolve("absent").toString());
            System.setProperty("info2ai.record",configDir.resolve("output").toString());
            Info2AiApi.start(Info2AiConfig.load(configDir));
            assertTrue(Info2AiApi.isRunning());assertFalse(Info2AiApi.realCapture());assertFalse(Info2AiApi.mock());
            assertNull(Info2AiApi.getSnapshot());assertEquals("unavailable",Info2AiApi.store().sourceStatus());
            assertTrue(Info2AiApi.store().warnings().contains("source_mode_conflict"));
            assertFalse(Files.exists(configDir.resolve("output")));
        } finally {
            Info2AiApi.stop();restore("info2ai.replay",replay);restore("info2ai.record",record);
        }
    }
    @Test public void reloadRejectsRecordReplayModeChanges() throws Exception {
        String record=System.getProperty("info2ai.record");
        try {
            System.clearProperty("info2ai.record");
            var config=Info2AiConfig.load(PersistentHistoryTest.directory("mode-reload-"));
            System.setProperty("info2ai.record","changed");
            assertThrows(Info2AiConfig.ModeChangeRequiresRestartException.class,config::reload);
        } finally {restore("info2ai.record",record);}
    }
    private static void restore(String name,String value) {if(value==null)System.clearProperty(name);else System.setProperty(name,value);}
}

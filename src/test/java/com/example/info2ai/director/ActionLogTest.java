package com.example.info2ai.director;

import com.example.info2ai.director.log.ActionLog;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActionLogTest {
    @Test
    public void persistsNextIdAcrossWriterLifetimes() throws Exception {
        var dir = Files.createTempDirectory("info2ai-director");
        try (var log = new ActionLog(dir)) {
            assertEquals(1, log.allocate());
            assertEquals(2, log.allocate());
        }
        try (var log = new ActionLog(dir)) {
            assertEquals(3, log.allocate());
        }
    }
}

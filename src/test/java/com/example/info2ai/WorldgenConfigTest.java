package com.example.info2ai;

import com.example.info2ai.config.Info2AiConfig;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class WorldgenConfigTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void ignoresLegacySettingsWithOneWarningAndKeepsRadius() throws Exception {
        var root = temporary.newFolder().toPath();
        var file = root.resolve("info2ai.json");
        Files.writeString(file, """
            {"worldgen":{"enabled":false,"port":-3,"url":{},"autoStart":false,
                         "timeoutMs":0,"migratedPrototypePort":true,"radius":1234}}
            """);
        var messages = new ArrayList<String>();
        var logger = (Logger) LogManager.getLogger("info2ai.config");
        var appender = new AbstractAppender("worldgen-test", null, null, false, null) {
            @Override public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            var config = Info2AiConfig.load(root);
            assertEquals(1234, config.worldgen().radius());
            assertFalse(config.worldgen().enabled());
            config.reload();
            assertEquals(List.of("worldgen.port/url/autoStart/timeoutMs 已不再使用（静态世界改为模组内计算）"), messages);
            var settings = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("worldgen");
            assertEquals(java.util.Set.of("enabled", "radius"), settings.keySet());
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test public void invalidRadiusBacksUpConfigAndUsesDefault() throws Exception {
        for (long radius : new long[]{0, 20001, 4294967297L}) {
            var root = temporary.newFolder().toPath();
            Files.writeString(root.resolve("info2ai.json"), "{\"worldgen\":{\"radius\":" + radius + "}}");
            assertEquals(2000, Info2AiConfig.load(root).worldgen().radius());
            assertTrue(Files.exists(root.resolve("info2ai.json.bad")));
        }
    }
}

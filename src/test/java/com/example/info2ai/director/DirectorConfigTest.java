package com.example.info2ai.director;

import com.example.info2ai.config.Info2AiConfig;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class DirectorConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void loadsAndPersistsDefaultsForEarlierConfigs() throws Exception {
        var root = temporary.newFolder().toPath();
        var file = root.resolve("info2ai.json");
        Files.writeString(file, "{\"capture\":{\"maxEntities\":12}}");
        var config = Info2AiConfig.load(root);
        var director = config.director();
        assertTrue(director.enabled());
        assertEquals("伙伴", director.speakerName());
        assertEquals(2, director.permissionLevel());
        assertEquals(10, director.ratePerMinute());
        assertEquals(120, director.checkPerMinute());
        assertEquals(4096, director.maxBlocksPerCommand());
        assertEquals(8, director.maxSummonPerCommand());
        assertEquals(32, director.maxActiveEntities());
        assertEquals(20, director.undoDepth());
        assertEquals(3000, director.commandTimeoutMs());
        assertFalse(director.allowModCommands());
        assertEquals(List.of("minecraft:wither", "minecraft:ender_dragon"), director.summonDenylist());
        assertEquals(List.of("minecraft:ore_*"), director.featureDenylist());
        assertEquals(17, director.smallStructures().size());
        assertTrue(director.smallStructures().contains("minecraft:ruined_portal_nether"));
        assertEquals(12, config.maxEntities());
        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("director");
        assertEquals(14, saved.size());
    }

    @Test
    public void reloadPreservesExplicitOverridesAndRecoversOnlyInvalidDirectorFields() throws Exception {
        var root = temporary.newFolder().toPath();
        var config = Info2AiConfig.load(root);
        var file = root.resolve("info2ai.json");
        Files.writeString(file, """
            {"http":{"port":28230},"director":{"enabled":false,"speakerName":"向导","permissionLevel":3,
              "ratePerMinute":0,"checkPerMinute":75,"maxBlocksPerCommand":-1,"maxSummonPerCommand":1.5,
              "maxActiveEntities":999999999999999,"undoDepth":5,"commandTimeoutMs":"no",
              "allowModCommands":true,"summonDenylist":[],"featureDenylist":["minecraft:ore_*",2],
              "smallStructures":["minecraft:swamp_hut"]}}
            """);
        config.reload();
        var director = config.director();
        assertFalse(director.enabled());
        assertEquals("向导", director.speakerName());
        assertEquals(3, director.permissionLevel());
        assertEquals(10, director.ratePerMinute());
        assertEquals(75, director.checkPerMinute());
        assertEquals(4096, director.maxBlocksPerCommand());
        assertEquals(8, director.maxSummonPerCommand());
        assertEquals(32, director.maxActiveEntities());
        assertEquals(5, director.undoDepth());
        assertEquals(3000, director.commandTimeoutMs());
        assertTrue(director.allowModCommands());
        assertTrue(director.summonDenylist().isEmpty());
        assertEquals(List.of("minecraft:ore_*"), director.featureDenylist());
        assertEquals(List.of("minecraft:swamp_hut"), director.smallStructures());
        assertEquals(28230, config.port());
        assertFalse(Files.exists(root.resolve("info2ai.json.bad")));
        assertEquals(director, Info2AiConfig.load(root).director());
    }

    @Test
    public void malformedDirectorSectionKeepsTheRestOfTheFile() throws Exception {
        var root = temporary.newFolder().toPath();
        Files.writeString(root.resolve("info2ai.json"), "{\"capture\":{\"maxEntities\":23},\"director\":null}");
        var config = Info2AiConfig.load(root);
        assertEquals(DirectorSettings.defaults(), config.director());
        assertEquals(23, config.maxEntities());
    }
}

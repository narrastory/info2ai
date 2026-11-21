package com.example.info2ai.staticworld;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class VanillaWorldgenFixtureTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void loadsOverworldNetherEnd() throws Exception {
        try (var fixture = new VanillaWorldgenFixture(temporary.newFolder().toPath())) {
            var overworld = fixture.handle("minecraft:overworld", 0L);
            assertFalse(overworld.placements().getStructureSets().isEmpty());
            assertEquals(-64, overworld.bottomY());
            assertEquals("minecraft:the_nether", fixture.handle("minecraft:the_nether", 0L).dimension());
            assertEquals("minecraft:the_end", fixture.handle("minecraft:the_end", 0L).dimension());
        }
    }
}

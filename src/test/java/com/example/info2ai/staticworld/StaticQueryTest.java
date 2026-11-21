package com.example.info2ai.staticworld;

import org.junit.Test;
import static org.junit.Assert.*;

public class StaticQueryTest {
    @Test public void keyIgnoresContext() {
        var first = new GenerationHandle(null, null, null, null, null, null, -64, 384, 0, "minecraft:overworld", true);
        var second = new GenerationHandle(null, null, null, null, null, null, 0, 256, 0, "minecraft:overworld", true);
        assertEquals(query(first, true).key(), query(second, true).key());
        assertTrue(query(first, true).supported());
        assertFalse(query(null, true).supported());
        assertFalse(query(first, false).supported());
    }

    private static StaticQuery query(GenerationHandle context, boolean enabled) {
        return new StaticQuery(1, "world", "0", "1.21.1", "minecraft:overworld", "custom",
            0, 64, 0, 2000, enabled, context);
    }
}

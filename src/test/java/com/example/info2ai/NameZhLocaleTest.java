package com.example.info2ai;

import com.example.info2ai.util.NameZh;
import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameZhLocaleTest {
    @Test public void readsRegistryNamesFromTheGameLocaleAndKeepsStructureNames() throws Exception {
        NameZh.initialize(new StringReader("""
            {"entity.minecraft.egg":"投掷物", "item.minecraft.egg":"鸡蛋",
             "biome.minecraft.plains":"平原", "gui.example.title":"Ignore me"}
            """));
        assertEquals("鸡蛋", NameZh.get("minecraft:egg"));
        assertEquals("平原", NameZh.get("minecraft:plains"));
        assertNotNull(NameZh.translated("minecraft:village_savanna"));
        assertNull(NameZh.translated("minecraft:title"));
    }
}

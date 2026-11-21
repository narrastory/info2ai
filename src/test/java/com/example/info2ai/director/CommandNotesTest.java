package com.example.info2ai.director;

import com.example.info2ai.director.catalog.CommandNotes;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommandNotesTest {
    @Test public void indexesAliasesAndKeepsOnlyExecutableExamples() {
        var notes = CommandNotes.load();
        assertEquals(notes.command("experience"), notes.command("xp"));
        assertTrue(notes.topic("guide").contains("系统会拦下的事"));
        assertTrue(notes.examples().stream().anyMatch(example -> example.command().startsWith("give @p")));
        assertFalse(notes.examples().stream().anyMatch(example -> example.command().startsWith("kill @")));
    }

    /** The companion loads the guide as one tool result, which is cut at 8000 characters after JSON encoding. */
    @Test public void guideStaysShortAndReferenceTopicsRemainAvailable() {
        var notes = CommandNotes.load();
        String guide = notes.topic("guide");
        int encoded = guide.length() + (int) guide.chars().filter(c -> c == '\n' || c == '"' || c == '\\').count();
        assertTrue("导演手册过长：" + encoded, encoded <= 7500);
        for (String topic : new String[] {"selectors", "coordinates", "text", "components", "entity_nbt", "block_nbt", "limits"}) {
            assertNotNull(topic, notes.topic(topic));
            assertFalse(topic, notes.topic(topic).isBlank());
        }
    }
}

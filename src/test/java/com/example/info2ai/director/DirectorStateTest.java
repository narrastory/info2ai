package com.example.info2ai.director;

import com.example.info2ai.capture.merge.SnapshotMerger;
import com.example.info2ai.http.MockSnapshotFactory;
import com.example.info2ai.model.Detail;
import com.example.info2ai.model.Facts;
import com.example.info2ai.model.ItemStackInfo;
import com.example.info2ai.model.Snapshot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorStateTest {
    @Test public void directorSectionSurvivesMergeAndIsOmittedInLite() {
        var base = MockSnapshotFactory.create().toJson(Detail.FULL);
        var director = new JsonObject();
        director.addProperty("enabled", true);
        base.add("director", director);
        base.getAsJsonObject("capabilities").addProperty("director", true);
        var merged = new SnapshotMerger().merge(Snapshot.fromJson(base), null, null);
        assertEquals(Facts.fromJson(director), merged.director());
        assertTrue(merged.hashes().containsKey("director"));
        assertTrue(merged.toJson(Detail.NORMAL).has("director"));
        assertFalse(merged.toJson(Detail.LITE).has("director"));
        assertTrue(merged.toJson(Detail.LITE).getAsJsonObject("capabilities").get("director").getAsBoolean());
    }

    @Test public void unmarkedLiveItemKeepsExplicitNullInNormalAndFull() {
        var raw = JsonParser.parseString("{\"id\":\"minecraft:bread\",\"director_action\":null}").getAsJsonObject();
        var item = ItemStackInfo.fromJson(raw);
        assertTrue(item.toJson(Detail.NORMAL).get("director_action").isJsonNull());
        assertTrue(item.toJson(Detail.FULL).get("director_action").isJsonNull());
        assertFalse(item.toJson(Detail.LITE).has("director_action"));
    }
}

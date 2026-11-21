package com.example.info2ai;

import com.example.info2ai.model.*;
import com.example.info2ai.util.JsonUtil;
import com.example.info2ai.util.VecUtil;
import com.google.gson.*;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class ContractTest {
    private JsonObject fixture(String detail) throws Exception {
        try (var stream = getClass().getResourceAsStream("/golden/state_" + detail + ".json")) {
            assertNotNull("缺少黄金样例", stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test public void minecraftYawUsesSouthAsZeroAndPreservesNegativeAngles() {
        assertEquals("S", VecUtil.facing(0));
        assertEquals("W", VecUtil.facing(90));
        assertEquals("N", VecUtil.facing(180));
        assertEquals("E", VecUtil.facing(270));
        assertEquals("E", VecUtil.facing(-90));
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        for (int i = 0; i < 8; i++) {
            assertEquals(names[i], VecUtil.facing(i * 45));
            assertEquals(names[i], VecUtil.facing(i * 45 + 22.499));
            assertEquals(names[(i + 1) % 8], VecUtil.facing(i * 45 + 22.5));
        }
    }

    @Test public void bearingsUseNorthAsZero() {
        assertEquals(0, VecUtil.bearing(0, -1), 0.001);
        assertEquals(90, VecUtil.bearing(1, 0), 0.001);
        assertEquals(180, VecUtil.bearing(0, 1), 0.001);
        assertEquals(270, VecUtil.bearing(-1, 0), 0.001);
        assertEquals("NW", VecUtil.directionFromDelta(-1, -1));
        assertEquals(5, VecUtil.horizontalDistance(0, 0, 3, 4), 0.001);
    }

    @Test public void jsonNumbersDoNotLoseSeedPrecision() {
        var data = new JsonObject();
        JsonUtil.putNullable(data, "seed", "-9223372036854775808");
        JsonUtil.putNullable(data, "optional", null);
        assertTrue(data.getAsJsonPrimitive("seed").isString());
        assertEquals("-9223372036854775808", data.get("seed").getAsString());
        assertTrue(data.has("optional") && data.get("optional").isJsonNull());
        assertEquals(0.3, JsonUtil.round2(0.1 + 0.2), 0);
        assertEquals(-1.24, JsonUtil.round2(-1.236), 0);
    }

    @Test public void calendarAdvancesAfterSleepWithoutChangingElapsedTicks() {
        assertEquals(1, TimeInfo.fromTicks(0).day().intValue());
        assertEquals(2, TimeInfo.fromTicks(24000).day().intValue());
        assertEquals("13:54", TimeInfo.fromTicks(7900).clock());
        assertEquals("20:18", TimeInfo.fromTicks(14300).clock());
        assertFalse(TimeInfo.fromTicks(14300).isDay());
    }

    @Test public void goldenRoundTripsAllDetailsAndFields() throws Exception {
        for (String level : List.of("lite", "normal", "full")) {
            JsonObject expected = fixture(level);
            Snapshot actual = Snapshot.fromJson(expected);
            assertEquals(level, actual.detail().jsonName());
            assertEquals("info2ai-test-world", actual.session().worldId());
            assertEquals("-9223372036854775808", actual.session().seed());
            assertEquals(12.0, actual.player().health(), 0);
            TimeInfo time=actual.world().time();
            assertEquals(TimeInfo.fromTicks((time.day()-1L)*24000+time.dayTime()),time);
            assertEquals(expected, actual.toJson());
            assertEquals(actual, Snapshot.fromJson(actual.toJson()));
        }
    }

    @Test public void inventoryKeepsEmptySlotsAndRejectsExternalMutation() throws Exception {
        Snapshot snapshot = Snapshot.fromJson(fixture("full"));
        assertEquals(36, snapshot.inventory().main().size());
        assertNull(snapshot.inventory().hotbar().get(1));
        assertEquals(9, snapshot.inventory().hotbar().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.inventory().hotbar().set(1, null));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.player().pos().set(0, 999.0));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.history().recentPositions().getFirst().set(0, 999.0));
    }

    @Test public void goldenStaticWorldUsesBuiltinSourceAndCoverage() throws Exception {
        for (String detail : List.of("normal", "full")) {
            var snapshot = Snapshot.fromJson(fixture(detail));
            var info = snapshot.world().staticWorld();
            assertEquals("builtin", info.source());
            assertEquals("high", info.confidence());
            assertEquals("high", snapshot.capabilities().staticWorldConfidence());
            var coverage = info.coverage().toJson();
            assertEquals("builtin", coverage.get("generator").getAsString());
            assertFalse(coverage.get("absence_proven").getAsBoolean());
            assertEquals(32, coverage.get("candidate_limit").getAsInt());
            assertTrue(coverage.has("truncated_by_budget"));
        }
    }

    @Test public void nestedJsonAndInputTreesCannotMutatePublishedSnapshot() throws Exception {
        JsonObject input = fixture("full");
        Snapshot snapshot = Snapshot.fromJson(input);
        input.getAsJsonObject("player").addProperty("health", 999);
        snapshot.toJson().getAsJsonObject("player").addProperty("health", 888);
        assertEquals(12.0, snapshot.player().health(), 0);
        JsonObject first = snapshot.inventory().aggregate().toJson();
        first.addProperty("torches", 999);
        assertEquals(43, snapshot.inventory().aggregate().toJson().get("torches").getAsInt());
    }

    @Test public void modelConstructorsCopyCallerOwnedCollections() {
        List<ItemStackInfo> slots = new ArrayList<>(Collections.nCopies(9, null));
        InventoryInfo inventory = new InventoryInfo(0,36,null,slots,null,null,null,null,null,null);
        slots.clear();
        assertEquals(9, inventory.hotbar().size());
        List<Signal> signals = new ArrayList<>();
        Snapshot snapshot = Snapshot.builder().signals(signals).build();
        signals.add(new Signal("test","info","test",0,null));
        assertTrue(snapshot.signals().isEmpty());
    }

    @Test public void detailProjectionKeepsHeavyFieldsOutOfLite() throws Exception {
        Snapshot full = Snapshot.fromJson(fixture("full"));
        JsonObject lite = full.toJson(Detail.LITE);
        assertFalse(lite.has("history"));
        assertFalse(lite.has("progress"));
        assertFalse(lite.getAsJsonObject("inventory").has("main"));
        assertFalse(lite.getAsJsonObject("world").has("static"));
        assertTrue(lite.getAsJsonObject("player").has("uuid"));
        assertTrue(lite.getAsJsonObject("session").has("world_id"));
    }

    @Test public void schemaRejectsUnknownVersionAndToleratesAdditionalFields() throws Exception {
        JsonObject input = fixture("lite");
        input.addProperty("future_field", true);
        assertNotNull(Snapshot.fromJson(input));
        input.addProperty("v", 2);
        assertThrows(IllegalArgumentException.class, () -> Snapshot.fromJson(input));
    }
}

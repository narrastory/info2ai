package com.example.info2ai;

import com.example.info2ai.capture.server.*;
import org.junit.Test;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class ServerLogicTest {
    @Test public void worldIdentityUsesNormalizedPathRatherThanDisplayName() {
        assertEquals(SessionSection.worldId(Path.of("saves/a")), SessionSection.worldId(Path.of("saves/a/../a")));
        assertNotEquals(SessionSection.worldId(Path.of("saves/a")), SessionSection.worldId(Path.of("saves/b")));
        assertTrue(SessionSection.worldId(Path.of("saves/a")).matches("[0-9a-f]{64}"));
    }

    @Test public void internalCandidatesRetainMoreThanResponseDefaultAndStaySortedAndBounded() {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 299; i >= 0; i--) candidates.add(i);
        var retained = EntityCandidates.retain(candidates, Integer::doubleValue);
        assertEquals(256, retained.size());
        assertEquals(Integer.valueOf(0), retained.getFirst());
        assertEquals(Integer.valueOf(255), retained.getLast());
    }

    @Test public void weatherStartsUnknownUntilContinuouslyObservedEdge() {
        var weather = new WeatherObservation();
        assertNull(weather.observe("overworld", 100, true, false));
        assertNull(weather.observe("overworld", 104, true, false));
        assertEquals(Integer.valueOf(0), weather.observe("overworld", 108, false, false));
        for (int tick = 112; tick <= 128; tick += 4) weather.observe("overworld", tick, false, false);
        assertEquals(Integer.valueOf(1), weather.observe("overworld", 132, false, false));
    }

    @Test public void weatherReturningAfterUnobservedRoundTripIsUnknownEvenWithSameFlags() {
        var weather = new WeatherObservation();
        weather.observe("overworld", 0, false, false);
        weather.observe("overworld", 4, true, false);
        weather.observe("nether", 8, false, false);
        assertNull(weather.observe("overworld", 12, true, false));
        assertNull(weather.observe("overworld", 16, true, false));
        assertNull(weather.observe("overworld", 0, false, false));
    }

    @Test public void weatherRapidDimensionRoundTripAlsoLosesContinuity() {
        var weather = new WeatherObservation();
        weather.observe("overworld", 0, false, false);
        weather.observe("overworld", 1, true, false);
        weather.observe("nether", 2, false, false);
        assertNull(weather.observe("overworld", 3, true, false));
    }

    @Test public void actualDimensionEventNotificationsInvalidateWeatherWithoutIntermediateCapture() {
        var section = new TimeSection();
        assertNull(section.observeWeather("A", 0, false, false));
        assertEquals(Integer.valueOf(0), section.observeWeather("A", 1, true, false));
        section.dimensionChanged(); // actual A -> B event, no collect in B
        section.dimensionChanged(); // actual B -> A event, still no intervening collect
        assertNull(section.observeWeather("A", 4, true, false));
        assertEquals(Integer.valueOf(0), section.observeWeather("A", 8, false, false));
        for (int tick = 12; tick <= 28; tick += 4) section.observeWeather("A", tick, false, false);
        assertEquals(Integer.valueOf(1), section.observeWeather("A", 28, false, false));
    }

    @Test public void damageTypeAndTagsTakePriorityOverAttacker() {
        assertEquals("explosion", DamageKind.classify("custom:blast", Set.of("explosion"), "mob"));
        assertEquals("explosion", DamageKind.classify("minecraft:player_explosion", Set.of("explosion"), "player"));
        assertEquals("magic", DamageKind.classify("minecraft:indirect_magic", Set.of(), "mob"));
        assertEquals("magic", DamageKind.classify("minecraft:magic", Set.of(), "player"));
        assertEquals("fire", DamageKind.classify("custom:flame", Set.of("fire"), "mob"));
        assertEquals("lava", DamageKind.classify("minecraft:lava", Set.of("fire"), null));
        assertEquals("mob", DamageKind.classify("minecraft:mob_attack", Set.of(), "mob"));
        assertEquals("player", DamageKind.classify("minecraft:player_attack", Set.of(), "player"));
    }

    @Test public void actualWorldDayOverridesCalendarIncludingFixedDimensionAndBoundary() {
        var fixedNoon = TimeSection.calendar(6000, false);
        assertFalse(fixedNoon.isDay());
        assertEquals(Integer.valueOf(6000), fixedNoon.dayTime());
        assertEquals("12:00", fixedNoon.clock());
        assertTrue(TimeSection.calendar(12050, true).isDay());
        assertFalse(TimeSection.calendar(11000, false).isDay());
    }

    @Test public void sharedLogBudgetChoosesErrorBeforeMissingNameAndSlowCapture() {
        var now = new AtomicLong();
        var budget = new CaptureLogBudget(now::get);
        List<String> output = new ArrayList<>();
        budget.offer(CaptureLogBudget.Priority.DEBUG, () -> output.add("missing"));
        budget.offer(CaptureLogBudget.Priority.DEBUG, () -> output.add("slow"));
        budget.offer(CaptureLogBudget.Priority.ERROR, () -> output.add("failure"));
        budget.flush();
        assertEquals(List.of("failure"), output);
        budget.offer(CaptureLogBudget.Priority.DEBUG, () -> output.add("second slow"));
        budget.flush();
        assertEquals(1, output.size());
        budget.offer(CaptureLogBudget.Priority.ERROR, () -> output.add("next failure"));
        now.set(999_999_999L);
        budget.flush();
        assertEquals(1, output.size());
        now.set(1_000_000_000L);
        budget.flush();
        assertEquals(List.of("failure", "next failure"), output);
        budget.offer(CaptureLogBudget.Priority.DEBUG, () -> output.add("old world"));
        budget.discardPending();
        now.set(2_000_000_000L);
        budget.flush();
        assertEquals(2, output.size());
    }
}

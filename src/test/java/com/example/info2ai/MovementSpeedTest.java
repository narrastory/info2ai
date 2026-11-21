package com.example.info2ai;

import com.example.info2ai.capture.server.MovementSpeed;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementSpeedTest {
    private void assertSpeed(double expected, Double actual, double tolerance) {
        assertNotNull("A continuous positive tick interval must provide speed", actual);
        assertEquals(expected, actual, tolerance);
    }

    @Test public void walkingAndSprintingUsePositionOverElapsedServerTicksWithoutVelocity() {
        var speed = new MovementSpeed();
        assertNull(speed.sample("overworld", 1, 4, 100, -26.46, -8.91));
        assertSpeed(4, speed.sample("overworld", 1, 4, 104, -26.46, -9.71), 1e-9);
        assertSpeed(7, speed.sample("overworld", 1, 4, 108, -26.46, -11.11), 1e-9);
    }

    @Test public void stationaryIsZeroAndHorizontalDiagonalUsesActualTickInterval() {
        var speed = new MovementSpeed();
        speed.sample("overworld", 1, 0, 0, 2, 3);
        assertSpeed(0, speed.sample("overworld", 1, 0, 4, 2, 3), 0);
        assertSpeed(5, speed.sample("overworld", 1, 0, 24, 5, 7), 0);
    }

    @Test public void duplicatePausedTickIsUnknownAndCannotAdvanceTheTimeBaseline() {
        var speed = new MovementSpeed();
        speed.sample("overworld", 1, 0, 0, 0, 0);
        assertSpeed(5, speed.sample("overworld", 1, 0, 4, 1, 0), 0);
        assertNull(speed.sample("overworld", 1, 0, 4, 2, 0));
        assertNull(speed.sample("overworld", 1, 0, 4, 2, 0));
        assertSpeed(5, speed.sample("overworld", 1, 0, 8, 2, 0), 0);
    }

    @Test public void smallAndLargeTeleportsNeverBecomeMovementSpeed() {
        var speed = new MovementSpeed();
        speed.sample("overworld", 1, 10, 0, 0, 0);
        assertSpeed(4, speed.sample("overworld", 1, 10, 4, .8, 0), 1e-9);
        assertNull(speed.sample("overworld", 1, 11, 8, .9, 0));
        assertSpeed(4, speed.sample("overworld", 1, 11, 12, 1.7, 0), 1e-9);
        assertNull(speed.sample("overworld", 1, 12, 16, 10000, 0));
        assertSpeed(0, speed.sample("overworld", 1, 12, 20, 10000, 0), 0);
    }

    @Test public void dimensionRoundTripNotificationsAndWorldDisconnectResetNeedNewBaseline() {
        var speed = new MovementSpeed();
        speed.sample("overworld", 1, 0, 0, 0, 0);
        assertSpeed(5, speed.sample("overworld", 1, 0, 4, 1, 0), 0);
        speed.reset(); // actual dimension event, before next capture
        speed.reset(); // return event, no intervening nether sample
        assertNull(speed.sample("overworld", 1, 0, 8, 100, 0));
        assertNull(speed.sample("nether", 1, 0, 12, 20, 0));
        assertSpeed(5, speed.sample("nether", 1, 0, 16, 21, 0), 0);
        speed.reset(); // disconnect / SERVER_STOPPING
        assertNull(speed.sample("overworld", 1, 0, 20, 1000, 0));
    }

    @Test public void respawnPlayerReplacementAndClockRollbackDoNotCreateSpeedSpikes() {
        var speed = new MovementSpeed();
        speed.sample("overworld", 1, 0, 100, 0, 0);
        assertSpeed(5, speed.sample("overworld", 1, 0, 104, 1, 0), 0);
        assertNull(speed.sample("overworld", 2, 0, 108, 300, 0));
        assertSpeed(5, speed.sample("overworld", 2, 0, 112, 301, 0), 0);
        assertNull(speed.sample("overworld", 2, 0, 4, 1000, 0));
        assertSpeed(5, speed.sample("overworld", 2, 0, 8, 1001, 0), 0);
    }
    @Test public void missingOrBrokenOptionalAccessorKeepsSpeedUnknownAndRecoveryRebaselines() {
        var speed = new MovementSpeed();
        com.example.info2ai.capture.server.TeleportSequence valid = () -> 7;
        assertNull(speed.observe(valid, "overworld", 1, 0, 0, 0));
        assertTrue(speed.sourceAvailable());
        assertSpeed(4, speed.observe(valid, "overworld", 1, 4, .8, 0), 1e-9);
        assertNull(speed.observe(new Object(), "overworld", 1, 8, 100, 0));
        assertFalse(speed.sourceAvailable());
        assertNull(speed.observe(valid, "overworld", 1, 12, 100.8, 0));
        assertSpeed(4, speed.observe(valid, "overworld", 1, 16, 101.6, 0), 1e-9);
        com.example.info2ai.capture.server.TeleportSequence broken = () -> { throw new AbstractMethodError("optional accessor missing"); };
        assertNull(speed.observe(broken, "overworld", 1, 20, 200, 0));
        assertFalse(speed.sourceAvailable());
        assertNull(speed.observe(valid, "overworld", 1, 24, 201, 0));
    }

}

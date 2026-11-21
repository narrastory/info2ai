package com.example.info2ai.capture.server;

import java.util.Objects;

/** Horizontal net displacement per elapsed game second (20 accumulated server ticks). */
public final class MovementSpeed {
    private record Frame(String dimension, int entityId, int teleportId, long tick, double x, double z) { }
    private Frame previous;
    private boolean sourceAvailable;

    public Double observe(Object teleportSource, String dimension, int entityId, long tick, double x, double z) {
        if (!(teleportSource instanceof TeleportSequence sequence)) {
            reset();
            return null;
        }
        final int teleportId;
        try {
            teleportId = sequence.info2ai$teleportSequence();
        } catch (RuntimeException | LinkageError unavailable) {
            reset();
            return null;
        }
        sourceAvailable = true;
        return sample(dimension, entityId, teleportId, tick, x, z);
    }

    public boolean sourceAvailable() {
        return sourceAvailable;
    }

    public Double sample(String dimension, int entityId, int teleportId, long tick, double x, double z) {
        Frame current = new Frame(dimension, entityId, teleportId, tick, x, z);
        if (previous == null || !Objects.equals(previous.dimension(), dimension) || previous.entityId() != entityId
            || previous.teleportId() != teleportId || tick < previous.tick()) {
            previous = current;
            return null;
        }
        long elapsed = tick - previous.tick();
        if (elapsed == 0) return null; // Repeated/fresh sample during one tick cannot move the baseline.
        double speed = Math.hypot(x - previous.x(), z - previous.z()) * 20.0 / elapsed;
        previous = current;
        return speed;
    }

    public void reset() {
        previous = null;
        sourceAvailable = false;
    }
}

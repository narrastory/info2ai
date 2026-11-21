package com.example.info2ai.capture.server;

import java.util.HashMap;
import java.util.Map;

/** Missing observations cannot establish a weather-change timestamp. */
public final class WeatherObservation {
    private record State(boolean rain, boolean thunder, long lastTick, Long changedTick) { }
    private final Map<String, State> states = new HashMap<>();
    private String lastDimension;

    public void invalidateContinuity() {
        lastDimension = null;
    }

    public Integer observe(String dimension, long tick, boolean rain, boolean thunder) {
        State previous = states.get(dimension);
        boolean continuous = dimension.equals(lastDimension) && previous != null && tick >= previous.lastTick() && tick - previous.lastTick() <= 4;
        Long changed = continuous ? previous.changedTick() : null;
        if (continuous && (previous.rain() != rain || previous.thunder() != thunder)) {
            changed = tick;
        }
        lastDimension = dimension;
        states.put(dimension, new State(rain, thunder, tick, changed));
        return changed == null ? null : (int)((tick - changed) / 20);
    }
}

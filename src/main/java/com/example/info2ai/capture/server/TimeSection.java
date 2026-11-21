package com.example.info2ai.capture.server;

import com.example.info2ai.model.*;
import net.minecraft.server.world.ServerWorld;

/** Calendar and actual dimension/daylight facts deliberately remain separate. */
public final class TimeSection {
    private final WeatherObservation weather = new WeatherObservation();

    public static TimeInfo calendar(long timeOfDay, boolean actualDay) {
        TimeInfo time = TimeInfo.fromTicks(timeOfDay);
        return new TimeInfo(time.day(), time.dayTime(), time.phase(), time.clock(), time.moonPhase(), actualDay);
    }

    public void dimensionChanged() {
        weather.invalidateContinuity();
    }

    public Integer observeWeather(String dimension, long tick, boolean rain, boolean thunder) {
        return weather.observe(dimension, tick, rain, thunder);
    }

    public WorldInfo collect(ServerWorld world, long serverTick) {
        String dimension = world.getRegistryKey().getValue().toString();
        Integer seconds = observeWeather(dimension, serverTick, world.isRaining(), world.isThundering());
        WeatherInfo observed = new WeatherInfo(world.isRaining(), world.isThundering(), (double)world.getRainGradient(1),
            (double)world.getThunderGradient(1), seconds);
        return new WorldInfo(dimension, dimension, calendar(world.getTimeOfDay(), world.isDay()), observed, null, null);
    }
}

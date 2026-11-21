package com.example.info2ai.staticworld;

import java.util.Arrays;
import java.util.Locale;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;

public class BuiltinStaticSourcePerformanceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void reportsOverworldStructureSearchTimes() throws Exception {
        long[] seeds = {0, 1, -1, 123456789, Long.MIN_VALUE};
        var times = new double[seeds.length];
        try (var fixture = new VanillaWorldgenFixture(temporary.newFolder().toPath())) {
            for (int i = 0; i < seeds.length; i++) {
                long seed = seeds[i];
                var handle = fixture.handle("minecraft:overworld", seed);
                // Actual server worlds begin vanilla ring placement calculation during construction.
                for (var set : handle.placements().getStructureSets()) {
                    if (set.value().placement() instanceof net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement rings) {
                        handle.placements().getPlacementPositions(rings);
                    }
                }
                var query = new StaticQuery(1, "world", Long.toString(seed), "1.21.1", handle.dimension(), "builtin",
                    0, 64, 0, 2000, true, handle);
                var result = BuiltinStaticSource.structuresAround(handle, 0, 0, query.candidateRadius(), BuiltinStaticSource.BUDGET_NANOS);
                var coverage = result.coverage().toJson();
                assertEquals(2091, coverage.get("candidate_radius").getAsInt());
                times[i] = coverage.get("elapsed_ms").getAsDouble();
                System.out.printf(Locale.ROOT, "BUILTIN PERF seed=%d elapsed_ms=%.1f chunks_checked=%d starts=%d truncated_by_budget=%s%n",
                    seed, times[i], coverage.get("chunks_checked").getAsInt(), coverage.get("candidates_found").getAsInt(),
                    coverage.get("truncated_by_budget").getAsBoolean());
            }
        }
        Arrays.sort(times);
        System.out.printf(Locale.ROOT, "BUILTIN PERF median_ms=%.1f max_ms=%.1f%n", times[2], times[4]);
    }
}

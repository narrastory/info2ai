package com.example.info2ai.staticworld;

import java.util.ArrayList;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.FixedBiomeSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BuiltinStaticSourceBiomeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void matchesVanillaReference() throws Exception {
        var rows = VanillaWorldgenFixture.reference().getAsJsonArray("biomes");
        assertEquals(30, rows.size());
        var mismatches = new ArrayList<String>();
        try (var fixture = new VanillaWorldgenFixture(temporary.newFolder().toPath())) {
            for (var entry : rows) {
                var row = entry.getAsJsonObject();
                String dimension = VanillaWorldgenFixture.dimension(row);
                var handle = fixture.handle(dimension, row.get("seed").getAsLong());
                var actual = BuiltinStaticSource.biomeAt(handle, row.get("x").getAsInt(),
                    row.get("y").getAsInt(), row.get("z").getAsInt()).toJson().get("id").getAsString();
                if (!row.get("id").getAsString().equals(actual)) mismatches.add(row + " actual=" + actual);
            }
        }
        assertTrue(String.join("\n", mismatches), mismatches.isEmpty());
        System.out.println("VANILLA BIOMES 30/30");
    }

    @Test public void anonymousBiomeStillReturnsStaticFacts() throws Exception {
        try (var fixture = new VanillaWorldgenFixture(temporary.newFolder().toPath())) {
            var flat = fixture.flatOverworld(0);
            var plains = flat.biomeSource().getBiomes().iterator().next().value();
            var unnamed = new Biome.Builder().temperature(0.8f).downfall(0.4f).effects(plains.getEffects())
                .spawnSettings(plains.getSpawnSettings()).generationSettings(plains.getGenerationSettings()).build();
            var handle = new GenerationHandle(flat.generator(), new FixedBiomeSource(RegistryEntry.of(unnamed)), flat.noise(),
                flat.placements(), flat.registries(), flat.templates(), flat.bottomY(), flat.height(), flat.seed(), flat.dimension(), false);
            var query = new StaticQuery(1, "world", "0", "1.21.1", flat.dimension(), "builtin", 0, 64, 0, 2000, true, handle);
            var result = new BuiltinStaticSource().getStatic(query);
            assertTrue(result.biome().toJson().get("id").isJsonNull());
            assertEquals("position", result.biome().toJson().get("mode").getAsString());
            assertTrue(result.coverage().toJson().get("biome_unregistered").getAsBoolean());
        }
    }
}

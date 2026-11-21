package com.example.info2ai.staticworld;

import com.example.info2ai.capture.merge.SnapshotStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class GenerationTrustTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void structuresDisabledReturnsBiomeOnly() throws Exception {
        try (var fixture = new VanillaWorldgenFixture(temporary.newFolder().toPath())) {
            var handle = fixture.handle("minecraft:overworld", 0);
            var disabled = new GenerationHandle(handle.generator(), handle.biomeSource(), handle.noise(), null,
                handle.registries(), handle.templates(), handle.bottomY(), handle.height(), handle.seed(), handle.dimension(), false);
            var query = new StaticQuery(1, "world", "0", "1.21.1", handle.dimension(), "builtin",
                0, 64, 0, 2000, true, disabled);
            var source = new BuiltinStaticSource();
            assertTrue(source.isAvailable(query));
            var result = source.getStatic(query);
            assertNotNull(result.biome().toJson().get("id"));
            assertTrue(result.structures().isEmpty());
            assertTrue(result.coverage().toJson().get("structures_disabled").getAsBoolean());
            assertEquals(0, result.coverage().toJson().get("chunks_checked").getAsInt());
        }
    }

    @Test public void builtinDiagnosticsKeepTheirShapeAndAreEpochFenced() {
        try (var store = new SnapshotStore()) {
            long epoch = store.worldEpoch();
            var facts = GenerationTrust.builtinFacts();
            store.staticGenerationContext(epoch, facts);
            var diagnostic = store.staticMetrics().getAsJsonObject("generation_context_detail");
            assertTrue(diagnostic.get("trusted").getAsBoolean());
            assertEquals("builtin_game_generation", diagnostic.get("reason").getAsString());
            store.invalidateWorld(epoch);
            assertFalse(store.staticMetrics().has("generation_context_detail"));
            store.staticGenerationContext(epoch, facts);
            assertFalse(store.staticMetrics().has("generation_context_detail"));
        }
    }
}

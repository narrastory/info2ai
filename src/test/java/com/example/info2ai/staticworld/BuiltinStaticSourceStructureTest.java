package com.example.info2ai.staticworld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureTerrainAdaptation;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.SpreadType;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.structure.BuriedTreasureStructure;
import net.minecraft.world.gen.structure.Structure;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BuiltinStaticSourceStructureTest {
    private static final long TEST_BUDGET = TimeUnit.SECONDS.toNanos(5);
    @ClassRule public static final TemporaryFolder TEMPORARY = new TemporaryFolder();
    private static VanillaWorldgenFixture fixture;

    @BeforeClass public static void loadVanilla() throws Exception {
        fixture = new VanillaWorldgenFixture(TEMPORARY.newFolder().toPath());
    }

    @AfterClass public static void closeVanilla() {
        if (fixture != null) fixture.close();
    }

    @Test public void findsEveryVanillaReferenceStart() throws Exception {
        var reference = VanillaWorldgenFixture.reference();
        assertEquals(20, reference.getAsJsonArray("structures").size());
        assertEquals(70, reference.getAsJsonArray("extended_structures").size());
        var mismatches = new ArrayList<String>();
        for (String group : List.of("structures", "extended_structures")) {
            for (var entry : reference.getAsJsonArray(group)) {
                var row = entry.getAsJsonObject();
                var handle = fixture.handle(VanillaWorldgenFixture.dimension(row), row.get("seed").getAsLong());
                int x = row.get("x").getAsInt(), z = row.get("z").getAsInt();
                String id = row.get("id").getAsString();
                var result = BuiltinStaticSource.structuresAround(handle, x, z, 300, TEST_BUDGET);
                if (result.structures().stream().noneMatch(item -> id.equals(item.structureId()) && item.x() == x && item.z() == z)) {
                    mismatches.add(row + " coverage=" + result.coverage().toJson() + " nearby=" + result.structures());
                }
            }
        }
        assertTrue(String.join("\n", mismatches), mismatches.isEmpty());
        System.out.println("VANILLA STRUCTURE STARTS 90/90");
    }

    @Test public void respectsBudget() {
        var handle = fixture.handle("minecraft:overworld", 0);
        var result = BuiltinStaticSource.structuresAround(handle, 0, 0, 20000, TimeUnit.MILLISECONDS.toNanos(1));
        assertTrue(result.coverage().toJson().get("truncated_by_budget").getAsBoolean());
    }

    @Test public void capsAt32SortedByDistance() {
        var handle = fixture.handle("minecraft:overworld", 0);
        var result = BuiltinStaticSource.structuresAround(handle, 0, 0, 6000, TEST_BUDGET);
        assertEquals(32, result.structures().size());
        var coverage = result.coverage().toJson();
        assertTrue(coverage.get("truncated").getAsBoolean());
        assertTrue(coverage.get("candidates_found").getAsInt() > 32);
        assertTrue(coverage.get("chunks_checked").getAsInt() <= 4096);
        double previous = -1;
        for (var item : result.structures()) {
            double distance = Math.hypot(item.x(), item.z());
            assertTrue(distance >= previous);
            previous = distance;
        }
    }

    @Test public void villageTypeAndIdResolved() throws Exception {
        var row = VanillaWorldgenFixture.reference().getAsJsonArray("structures").get(0).getAsJsonObject();
        var handle = fixture.handle("minecraft:overworld", row.get("seed").getAsLong());
        var result = BuiltinStaticSource.structuresAround(handle, row.get("x").getAsInt(), row.get("z").getAsInt(), 300, TEST_BUDGET);
        var village = result.structures().stream().filter(item -> item.structureId().equals(row.get("id").getAsString())).findFirst().orElseThrow();
        assertEquals("minecraft:village", village.type());
        assertTrue(village.structureId().startsWith("minecraft:village_"));
        assertEquals("resolved", village.variantStatus());
    }

    @Test public void noWorldAccess() {
        var handle = fixture.handle("minecraft:the_nether", 0);
        var result = BuiltinStaticSource.structuresAround(handle, 0, 0, 300, TEST_BUDGET);
        assertTrue(result.coverage().toJson().get("chunks_checked").getAsInt() > 0);
    }

    @Test public void flatGeneratorUsesItsOwnPlacementOverridesAndBiomeSource() {
        var flat = fixture.flatOverworld(0);
        assertEquals("minecraft:plains", BuiltinStaticSource.biomeAt(flat, 0, 64, 0).toJson().get("id").getAsString());
        assertFalse(flat.placements().getStructureSets().isEmpty());
        var result = BuiltinStaticSource.structuresAround(flat, 0, 0, 1000, TEST_BUDGET);
        assertTrue(result.coverage().toJson().get("chunks_checked").getAsInt() > 0);
    }

    @Test public void inlineStructureWithoutRegistryIdDoesNotBreakTheSearch() {
        var flat = fixture.flatOverworld(0);
        var config = new Structure.Config(RegistryEntryList.of(List.copyOf(flat.biomeSource().getBiomes())),
            Map.of(), GenerationStep.Feature.UNDERGROUND_STRUCTURES, StructureTerrainAdaptation.NONE);
        var placement = new RandomSpreadStructurePlacement(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT,
            1, 0, Optional.empty(), 1, 0, SpreadType.LINEAR);
        var set = new StructureSet(RegistryEntry.of(new BuriedTreasureStructure(config)), placement);
        var handle = withStructures(flat, List.of(set));
        var result = BuiltinStaticSource.structuresAround(handle, 0, 0, 1, TEST_BUDGET);
        assertEquals(1, result.coverage().toJson().get("chunks_checked").getAsInt());
        assertTrue(result.structures().isEmpty());
    }

    @Test public void sharedStructureInTheSameChunkUsesTheFirstStructureSet() {
        var flat = fixture.flatOverworld(0);
        var structure = mineshaft(flat);
        var first = new StructureSet(structure, spread(new Vec3i(4, 0, 0)));
        var second = new StructureSet(structure, spread(new Vec3i(-4, 0, 0)));
        var result = BuiltinStaticSource.structuresAround(withStructures(flat, List.of(first, second)), 0, 0, 8, TEST_BUDGET);
        assertEquals(1, result.structures().size());
        assertEquals(4, result.structures().getFirst().x().intValue());
    }

    @Test public void largeRadiusChecksNearbyStartsBeforeSlowDenseDistantRegions() {
        var flat = fixture.flatOverworld(0);
        var structure = mineshaft(flat);
        var dense = new RandomSpreadStructurePlacement(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT,
            0, 0, Optional.empty(), 1, 0, SpreadType.LINEAR) {
            @Override public ChunkPos getStartChunk(long seed, int x, int z) {
                if (Math.abs(x) > 64 || Math.abs(z) > 64) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(15));
                return super.getStartChunk(seed, x, z);
            }
        };
        var handle = withStructures(flat, List.of(new StructureSet(structure, dense), new StructureSet(structure, spread(Vec3i.ZERO))));
        assertFalse(BuiltinStaticSource.structuresAround(handle, 0, 0, 1, TEST_BUDGET).structures().isEmpty());
        var result = BuiltinStaticSource.structuresAround(handle, 0, 0, 20000, TimeUnit.MILLISECONDS.toNanos(500));
        assertFalse(result.structures().isEmpty());
        assertEquals(0, result.structures().getFirst().x().intValue());
        assertEquals(0, result.structures().getFirst().z().intValue());
    }

    private static RandomSpreadStructurePlacement spread(Vec3i offset) {
        return new RandomSpreadStructurePlacement(offset, StructurePlacement.FrequencyReductionMethod.DEFAULT,
            1, 0, Optional.empty(), 1, 0, SpreadType.LINEAR);
    }

    private static RegistryEntry<Structure> mineshaft(GenerationHandle handle) {
        return handle.registries().get(RegistryKeys.STRUCTURE).getEntry(Identifier.ofVanilla("mineshaft")).orElseThrow();
    }

    private static GenerationHandle withStructures(GenerationHandle flat, List<StructureSet> sets) {
        var placements = StructurePlacementCalculator.create(flat.noise(), flat.seed(), flat.biomeSource(), sets.stream().map(RegistryEntry::of));
        return new GenerationHandle(flat.generator(), flat.biomeSource(), flat.noise(), placements, flat.registries(),
            flat.templates(), flat.bottomY(), flat.height(), flat.seed(), flat.dimension(), true);
    }
}

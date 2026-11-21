package com.example.info2ai.staticworld;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;

/** References needed for generation calculations without a live world or chunks. */
public record GenerationHandle(
        ChunkGenerator generator, BiomeSource biomeSource, NoiseConfig noise,
        StructurePlacementCalculator placements, DynamicRegistryManager registries,
        StructureTemplateManager templates, int bottomY, int height, long seed, String dimension,
        boolean structuresEnabled) {
}

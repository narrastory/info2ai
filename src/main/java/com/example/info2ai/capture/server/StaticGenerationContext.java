package com.example.info2ai.capture.server;

import com.example.info2ai.staticworld.GenerationHandle;
import net.minecraft.server.world.ServerWorld;

final class StaticGenerationContext {
    private StaticGenerationContext() { }

    /** Called by ServerCapture on the owning server thread. */
    static GenerationHandle capture(ServerWorld world) {
        var server = world.getServer();
        var manager = world.getChunkManager();
        var generator = manager.getChunkGenerator();
        boolean structuresEnabled = server.getSaveProperties().getGeneratorOptions().shouldGenerateStructures();
        return new GenerationHandle(generator, generator.getBiomeSource(), manager.getNoiseConfig(),
            manager.getStructurePlacementCalculator(), server.getRegistryManager(), server.getStructureTemplateManager(),
            world.getBottomY(), world.getHeight(), world.getSeed(), world.getRegistryKey().getValue().toString(), structuresEnabled);
    }
}

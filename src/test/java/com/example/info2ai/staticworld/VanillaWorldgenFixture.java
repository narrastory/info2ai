package com.example.info2ai.staticworld;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.Schemas;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.*;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.path.SymlinkValidationException;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.level.storage.LevelStorage;

/** Loads vanilla resources into an empty temporary template directory, without a world. */
final class VanillaWorldgenFixture implements AutoCloseable {
    static {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    private final LifecycledResourceManager resources;
    private final DynamicRegistryManager registries;
    private final StructureTemplateManager templates;
    private final Map<RegistryKey<DimensionOptions>, DimensionOptions> dimensions;
    private final Map<WorldKey, GenerationHandle> handles = new HashMap<>();
    private record WorldKey(String dimension, long seed) { }

    VanillaWorldgenFixture(Path directory) throws IOException, SymlinkValidationException {
        try (var session = LevelStorage.create(directory).createSession("templates")) {
            resources = new LifecycledResourceManagerImpl(ResourceType.SERVER_DATA,
                List.of(VanillaDataPackProvider.createDefaultPack()));
            try {
                var base = DynamicRegistryManager.of(Registries.REGISTRIES);
                var dynamic = RegistryLoader.loadFromResource(resources, base, RegistryLoader.DYNAMIC_REGISTRIES);
                registries = new DynamicRegistryManager.ImmutableImpl(Stream.concat(base.streamAllRegistries(),
                    dynamic.streamAllRegistries())).toImmutable();
                registries.streamAllRegistries().forEach(entry -> bindTags(entry, resources));
                templates = new StructureTemplateManager(resources, session, Schemas.getFixer(),
                    Registries.BLOCK.getReadOnlyWrapper());
                dimensions = dynamic.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.DEFAULT)
                    .createDimensionsRegistryHolder().dimensions();
            } catch (RuntimeException | Error failure) {
                resources.close();
                throw failure;
            }
        }
    }

    GenerationHandle handle(String dimension, long seed) {
        return handles.computeIfAbsent(new WorldKey(dimension, seed), key -> createHandle(key.dimension(), key.seed()));
    }

    private GenerationHandle createHandle(String dimension, long seed) {
        var key = RegistryKey.of(RegistryKeys.DIMENSION, Identifier.of(dimension));
        var generator = (NoiseChunkGenerator) dimensions.get(key).chunkGenerator();
        var noise = NoiseConfig.create(generator.getSettings().value(),
            registries.get(RegistryKeys.NOISE_PARAMETERS).getReadOnlyWrapper(), seed);
        var biomeSource = generator.getBiomeSource();
        var placements = StructurePlacementCalculator.create(noise, seed, biomeSource,
            registries.get(RegistryKeys.STRUCTURE_SET).getReadOnlyWrapper());
        return new GenerationHandle(generator, biomeSource, noise, placements, registries, templates,
            generator.getMinimumY(), generator.getWorldHeight(), seed, dimension, true);
    }

    GenerationHandle flatOverworld(long seed) {
        var generator = registries.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).getOverworld().orElseThrow().chunkGenerator();
        var noise = NoiseConfig.create(ChunkGeneratorSettings.createMissingSettings(),
            registries.get(RegistryKeys.NOISE_PARAMETERS).getReadOnlyWrapper(), seed);
        var placements = generator.createStructurePlacementCalculator(registries.get(RegistryKeys.STRUCTURE_SET).getReadOnlyWrapper(), noise, seed);
        return new GenerationHandle(generator, generator.getBiomeSource(), noise, placements, registries, templates,
            -64, 384, seed, "minecraft:overworld", true);
    }

    static JsonObject reference() throws IOException {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                VanillaWorldgenFixture.class.getResourceAsStream("/static/vanilla-1.21.1.json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    static String dimension(JsonObject row) {
        return row.has("dimension") ? row.get("dimension").getAsString() : "minecraft:overworld";
    }

    private static <T> void bindTags(DynamicRegistryManager.Entry<T> entry, ResourceManager resources) {
        var registry = entry.value();
        var loaded = new TagGroupLoader<RegistryEntry<T>>(registry::getEntry,
            RegistryKeys.getTagPath(entry.key())).load(resources);
        Map<TagKey<T>, List<RegistryEntry<T>>> tags = new HashMap<>();
        loaded.forEach((id, values) -> tags.put(TagKey.of(entry.key(), id), List.copyOf(values)));
        registry.populateTags(tags);
    }

    @Override public void close() {
        resources.close();
    }
}

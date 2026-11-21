package com.example.info2ai.director.catalog;

import com.example.info2ai.api.DirectorRequests.LookupEntry;
import com.example.info2ai.api.DirectorRequests.LookupKinds;
import com.example.info2ai.api.DirectorRequests.LookupResult;
import com.example.info2ai.director.DirectorSettings;
import com.example.info2ai.util.NameZh;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public record RegistryCatalog(Map<LookupKind, List<Entry>> entries) {
    record Entry(String id, String zh, String name) { }
    private record Match(Entry entry, int rank) { }

    public static RegistryCatalog build(MinecraftServer server) {
        var data = new EnumMap<LookupKind, List<Entry>>(LookupKind.class);
        var registries = server.getRegistryManager();
        add(data, LookupKind.ITEM, Registries.ITEM.getIds().stream(), id -> Registries.ITEM.get(id).getName().getString(), false);
        add(data, LookupKind.BLOCK, Registries.BLOCK.getIds().stream(), id -> Registries.BLOCK.get(id).getName().getString(), false);
        add(data, LookupKind.ENTITY, Registries.ENTITY_TYPE.getIds().stream(), id -> Registries.ENTITY_TYPE.get(id).getName().getString(), false);
        add(data, LookupKind.EFFECT, Registries.STATUS_EFFECT.getIds().stream(), id -> Registries.STATUS_EFFECT.get(id).getName().getString(), false);
        add(data, LookupKind.ENCHANTMENT, registries.get(RegistryKeys.ENCHANTMENT).getIds().stream(),
            id -> registries.get(RegistryKeys.ENCHANTMENT).get(id).description().getString(), false);
        ids(data, LookupKind.POTION, Registries.POTION.getIds().stream());
        ids(data, LookupKind.PARTICLE, Registries.PARTICLE_TYPE.getIds().stream());
        ids(data, LookupKind.SOUND, Registries.SOUND_EVENT.getIds().stream());
        add(data, LookupKind.ATTRIBUTE, Registries.ATTRIBUTE.getIds().stream(),
            id -> net.minecraft.text.Text.translatable(Registries.ATTRIBUTE.get(id).getTranslationKey()).getString(), false);
        ids(data, LookupKind.BIOME, registries.get(RegistryKeys.BIOME).getIds().stream());
        ids(data, LookupKind.STRUCTURE, registries.get(RegistryKeys.STRUCTURE).getIds().stream());
        ids(data, LookupKind.FEATURE, registries.get(RegistryKeys.CONFIGURED_FEATURE).getIds().stream());
        ids(data, LookupKind.TEMPLATE, server.getStructureTemplateManager().streamTemplates());
        ids(data, LookupKind.LOOT_TABLE, server.getReloadableRegistries().getIds(RegistryKeys.LOOT_TABLE).stream());
        ids(data, LookupKind.COMPONENT, Registries.DATA_COMPONENT_TYPE.getIds().stream());
        add(data, LookupKind.BLOCK_TAG, Registries.BLOCK.streamTags().map(tag -> tag.id()), Identifier::getPath, true);
        add(data, LookupKind.ITEM_TAG, Registries.ITEM.streamTags().map(tag -> tag.id()), Identifier::getPath, true);
        add(data, LookupKind.ENTITY_TAG, Registries.ENTITY_TYPE.streamTags().map(tag -> tag.id()), Identifier::getPath, true);
        ids(data, LookupKind.ADVANCEMENT, server.getAdvancementLoader().getAdvancements().stream().map(advancement -> advancement.id()));
        return new RegistryCatalog(Map.copyOf(data));
    }

    private static void ids(Map<LookupKind, List<Entry>> target, LookupKind kind, Stream<Identifier> ids) {
        add(target, kind, ids, Identifier::getPath, false);
    }

    private static void add(Map<LookupKind, List<Entry>> target, LookupKind kind, Stream<Identifier> ids,
                            Function<Identifier, String> name, boolean tag) {
        target.put(kind, ids.distinct().map(id -> new Entry((tag ? "#" : "") + id,
            NameZh.translated(id.toString()), name.apply(id))).sorted(Comparator.comparing(Entry::id)).toList());
    }

    public LookupResult lookup(String kind, String query, int limit, DirectorSettings settings) {
        if (kind == null) return new LookupResult(null, null, 0, false, List.of(),
            new LookupKinds(Arrays.stream(LookupKind.values()).map(LookupKind::id).toList()));
        LookupKind type;
        try { type = LookupKind.valueOf(kind.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return null; }
        String text = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        var matches = entries.getOrDefault(type, List.of()).stream().map(entry -> new Match(entry, rank(entry, text)))
            .filter(match -> match.rank() < 4).sorted(Comparator.comparingInt(Match::rank).thenComparing(m -> m.entry().id())).toList();
        var first = matches.stream().limit(limit).map(Match::entry).map(entry ->
            new LookupEntry(entry.id(), entry.zh(), entry.name(),
                type == LookupKind.ENTITY ? !settings.summonDenylist().contains(entry.id()) : null,
                type == LookupKind.STRUCTURE ? settings.smallStructures().contains(entry.id()) : null)).toList();
        return new LookupResult(type.id(), query == null ? "" : query, matches.size(), matches.size() > limit, first);
    }

    private static int rank(Entry entry, String query) {
        String path = entry.id().substring(entry.id().indexOf(':') + 1);
        if (query.isEmpty() || query.equals(path) || query.equals(entry.id()) || query.equals(entry.zh())
            || query.equalsIgnoreCase(entry.name())) return 0;
        if (path.startsWith(query) || entry.id().startsWith(query)) return 1;
        if (query.equals("花") && (entry.id().equals("minecraft:poppy") || entry.id().equals("minecraft:dandelion"))) return 1;
        if (entry.zh() != null && entry.zh().contains(query)
            || entry.name().toLowerCase(Locale.ROOT).contains(query)) return 2;
        return entry.id().contains(query) ? 3 : 4;
    }
}

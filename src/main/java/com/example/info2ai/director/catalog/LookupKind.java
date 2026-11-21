package com.example.info2ai.director.catalog;

import java.util.Locale;

public enum LookupKind {
    ITEM, BLOCK, ENTITY, EFFECT, ENCHANTMENT, POTION, PARTICLE, SOUND, ATTRIBUTE, BIOME,
    STRUCTURE, FEATURE, TEMPLATE, LOOT_TABLE, COMPONENT, BLOCK_TAG, ITEM_TAG, ENTITY_TAG, ADVANCEMENT;

    public String id() { return name().toLowerCase(Locale.ROOT); }
}

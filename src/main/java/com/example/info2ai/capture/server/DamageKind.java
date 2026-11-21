package com.example.info2ai.capture.server;

import java.util.Set;

/** Authoritative type/tag categories precede attacker identity, which has its own field. */
public final class DamageKind {
    private DamageKind() { }

    public static String classify(String type, Set<String> tags, String attacker) {
        if (tags.contains("explosion")) return "explosion";
        String named = switch (type) {
            case "minecraft:explosion", "minecraft:player_explosion", "minecraft:fireworks", "minecraft:bad_respawn_point" -> "explosion";
            case "minecraft:magic", "minecraft:indirect_magic", "minecraft:wither", "minecraft:dragon_breath", "minecraft:thorns" -> "magic";
            case "minecraft:lava" -> "lava";
            case "minecraft:in_fire", "minecraft:on_fire", "minecraft:campfire", "minecraft:hot_floor" -> "fire";
            case "minecraft:fall", "minecraft:fly_into_wall", "minecraft:stalagmite" -> "fall";
            case "minecraft:drown" -> "drown";
            case "minecraft:starve" -> "starve";
            case "minecraft:out_of_world" -> "void";
            default -> null;
        };
        if (named != null) return named;
        if (tags.contains("fire")) return "fire";
        if (tags.contains("fall")) return "fall";
        if (tags.contains("drowning")) return "drown";
        return attacker == null ? "other" : attacker;
    }
}

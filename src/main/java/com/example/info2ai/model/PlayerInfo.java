package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record PlayerInfo(
    String name,
    String uuid,
    List<Double> pos,
    List<Double> posBlock,
    String dimension,
    Double yaw,
    Double pitch,
    String facing,
    List<Double> velocity,
    Double speedMps,
    Boolean onGround,
    Boolean inWater,
    Boolean inLava,
    Boolean inPowderSnow,
    Boolean submerged,
    Boolean sprinting,
    Boolean sneaking,
    Boolean swimming,
    Boolean gliding,
    Boolean flying,
    Boolean fallFlying,
    Boolean climbing,
    Boolean onFire,
    Integer frozenTicks,
    Facts riding,
    Boolean sleeping,
    Double health,
    Double maxHealth,
    Double absorption,
    Double armor,
    Double toughness,
    Integer food,
    Double saturation,
    Double exhaustion,
    Integer air,
    Integer maxAir,
    Integer xpLevel,
    Double xpProgress,
    Integer xpTotal,
    Integer fireTicks,
    Double fallDistance,
    Facts lastDamage,
    List<Facts> effects,
    Facts attributes,
    ItemStackInfo held,
    ItemStackInfo offhand,
    Integer selectedSlot,
    Facts respawn,
    String gameMode,
    String screen,
    Facts lookingAt,
    Facts inputs,
    Facts view,
    Facts activity
) implements JsonModel {
    public PlayerInfo {
        pos = JsonUtil.immutableSlots(pos);
        posBlock = JsonUtil.immutableSlots(posBlock);
        velocity = JsonUtil.immutableSlots(velocity);
        effects = JsonUtil.immutableSlots(effects);
    }

    public static PlayerInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new PlayerInfo(
            JsonUtil.string(o, "name"),
            JsonUtil.string(o, "uuid"),
            JsonUtil.doubles(o, "pos"),
            JsonUtil.doubles(o, "pos_block"),
            JsonUtil.string(o, "dimension"),
            JsonUtil.number(o, "yaw"),
            JsonUtil.number(o, "pitch"),
            JsonUtil.string(o, "facing"),
            JsonUtil.doubles(o, "velocity"),
            JsonUtil.number(o, "speed_mps"),
            JsonUtil.bool(o, "on_ground"),
            JsonUtil.bool(o, "in_water"),
            JsonUtil.bool(o, "in_lava"),
            JsonUtil.bool(o, "in_powder_snow"),
            JsonUtil.bool(o, "submerged"),
            JsonUtil.bool(o, "sprinting"),
            JsonUtil.bool(o, "sneaking"),
            JsonUtil.bool(o, "swimming"),
            JsonUtil.bool(o, "gliding"),
            JsonUtil.bool(o, "flying"),
            JsonUtil.bool(o, "fall_flying"),
            JsonUtil.bool(o, "climbing"),
            JsonUtil.bool(o, "on_fire"),
            JsonUtil.integer(o, "frozen_ticks"),
            Facts.fromJson(JsonUtil.object(o, "riding")),
            JsonUtil.bool(o, "sleeping"),
            JsonUtil.number(o, "health"),
            JsonUtil.number(o, "max_health"),
            JsonUtil.number(o, "absorption"),
            JsonUtil.number(o, "armor"),
            JsonUtil.number(o, "toughness"),
            JsonUtil.integer(o, "food"),
            JsonUtil.number(o, "saturation"),
            JsonUtil.number(o, "exhaustion"),
            JsonUtil.integer(o, "air"),
            JsonUtil.integer(o, "max_air"),
            JsonUtil.integer(o, "xp_level"),
            JsonUtil.number(o, "xp_progress"),
            JsonUtil.integer(o, "xp_total"),
            JsonUtil.integer(o, "fire_ticks"),
            JsonUtil.number(o, "fall_distance"),
            Facts.fromJson(JsonUtil.object(o, "last_damage")),
            JsonUtil.list(o, "effects", e -> Facts.fromJson(e.getAsJsonObject())),
            Facts.fromJson(JsonUtil.object(o, "attributes")),
            ItemStackInfo.fromJson(JsonUtil.object(o, "held")),
            ItemStackInfo.fromJson(JsonUtil.object(o, "offhand")),
            JsonUtil.integer(o, "selected_slot"),
            Facts.fromJson(JsonUtil.object(o, "respawn")),
            JsonUtil.string(o, "game_mode"),
            JsonUtil.string(o, "screen"),
            Facts.fromJson(JsonUtil.object(o, "looking_at")),
            Facts.fromJson(JsonUtil.object(o, "inputs")),
            Facts.fromJson(JsonUtil.object(o, "view")),
            Facts.fromJson(JsonUtil.object(o, "activity"))
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "name", name, detail);
        JsonUtil.put(o, "uuid", uuid, detail);
        JsonUtil.put(o, "pos", pos == null ? null : pos.stream().map(JsonUtil::round2).toList(), detail);
        JsonUtil.put(o, "pos_block", posBlock == null ? null : posBlock.stream().map(JsonUtil::round2).toList(), detail);
        JsonUtil.put(o, "dimension", dimension, detail);
        JsonUtil.put(o, "yaw", yaw == null ? null : JsonUtil.round1(yaw), detail);
        JsonUtil.put(o, "pitch", pitch == null ? null : JsonUtil.round1(pitch), detail);
        JsonUtil.put(o, "facing", facing, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "velocity", velocity == null ? null : velocity.stream().map(JsonUtil::round3).toList(), detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "speed_mps", speedMps == null ? null : JsonUtil.round3(speedMps), detail);
        JsonUtil.put(o, "on_ground", onGround, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "in_water", inWater, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "in_lava", inLava, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "in_powder_snow", inPowderSnow, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "submerged", submerged, detail);
        JsonUtil.put(o, "sprinting", sprinting, detail);
        JsonUtil.put(o, "sneaking", sneaking, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "swimming", swimming, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "gliding", gliding, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "flying", flying, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "fall_flying", fallFlying, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "climbing", climbing, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "on_fire", onFire, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "frozen_ticks", frozenTicks, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "riding", riding, detail);
        JsonUtil.put(o, "sleeping", sleeping, detail);
        JsonUtil.put(o, "health", health == null ? null : JsonUtil.round3(health), detail);
        JsonUtil.put(o, "max_health", maxHealth == null ? null : JsonUtil.round3(maxHealth), detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "absorption", absorption == null ? null : JsonUtil.round3(absorption), detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "armor", armor == null ? null : JsonUtil.round3(armor), detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "toughness", toughness == null ? null : JsonUtil.round3(toughness), detail);
        JsonUtil.put(o, "food", food, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "saturation", saturation == null ? null : JsonUtil.round3(saturation), detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "exhaustion", exhaustion == null ? null : JsonUtil.round3(exhaustion), detail);
        JsonUtil.put(o, "air", air, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "max_air", maxAir, detail);
        JsonUtil.put(o, "xp_level", xpLevel, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "xp_progress", xpProgress == null ? null : JsonUtil.round3(xpProgress), detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "xp_total", xpTotal, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "fire_ticks", fireTicks, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "fall_distance", fallDistance == null ? null : JsonUtil.round3(fallDistance), detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "last_damage", lastDamage, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "effects", effects, detail);
        if (detail == Detail.FULL) JsonUtil.put(o, "attributes", attributes, detail);
        JsonUtil.put(o, "held", held, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "offhand", offhand, detail);
        JsonUtil.put(o, "selected_slot", selectedSlot, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "respawn", respawn, detail);
        JsonUtil.put(o, "game_mode", gameMode, detail);
        JsonUtil.put(o, "screen", screen, detail);
        JsonUtil.put(o, "looking_at", lookingAt, detail);
        if (detail == Detail.FULL) JsonUtil.put(o, "inputs", inputs, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "view", view, detail);
        if (detail != Detail.LITE) JsonUtil.put(o, "activity", activity, detail);
        return o;
    }
}

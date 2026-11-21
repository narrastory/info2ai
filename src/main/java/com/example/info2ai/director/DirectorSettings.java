package com.example.info2ai.director;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;
import java.util.stream.StreamSupport;

public record DirectorSettings(boolean enabled, String speakerName, int permissionLevel, int ratePerMinute,
                               int checkPerMinute, int maxBlocksPerCommand, int maxSummonPerCommand,
                               int maxActiveEntities, int undoDepth, int commandTimeoutMs, boolean allowModCommands,
                               List<String> summonDenylist, List<String> featureDenylist, List<String> smallStructures) {
    private static final Logger LOG = LoggerFactory.getLogger("info2ai.config");

    public DirectorSettings {
        summonDenylist = List.copyOf(summonDenylist);
        featureDenylist = List.copyOf(featureDenylist);
        smallStructures = List.copyOf(smallStructures);
    }

    public static DirectorSettings defaults() {
        return new DirectorSettings(true, "伙伴", 2, 10, 120, 4096, 8, 32, 20, 3000, false,
            List.of("minecraft:wither", "minecraft:ender_dragon"),
            List.of("minecraft:ore_*"),
            List.of("minecraft:igloo", "minecraft:shipwreck", "minecraft:shipwreck_beached", "minecraft:swamp_hut",
                "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:ocean_ruin_cold",
                "minecraft:ocean_ruin_warm", "minecraft:buried_treasure", "minecraft:nether_fossil",
                "minecraft:ruined_portal", "minecraft:ruined_portal_desert", "minecraft:ruined_portal_jungle",
                "minecraft:ruined_portal_swamp", "minecraft:ruined_portal_mountain", "minecraft:ruined_portal_ocean",
                "minecraft:ruined_portal_nether"));
    }

    /** 无效字段单独恢复默认值，保留已有配置及未知字段。 */
    public static DirectorSettings read(JsonObject config) {
        JsonElement section = config.get("director");
        JsonObject object;
        if (section != null && section.isJsonObject()) {
            object = section.getAsJsonObject();
        } else {
            if (section != null) LOG.warn("配置 director 必须是对象，已使用默认值");
            object = new JsonObject();
            config.add("director", object);
        }
        DirectorSettings defaults = defaults();
        return new DirectorSettings(
            read(object, "enabled", defaults.enabled(), DirectorSettings::bool),
            read(object, "speakerName", defaults.speakerName(), DirectorSettings::nonBlankString),
            read(object, "permissionLevel", defaults.permissionLevel(), DirectorSettings::positiveInt),
            read(object, "ratePerMinute", defaults.ratePerMinute(), DirectorSettings::positiveInt),
            read(object, "checkPerMinute", defaults.checkPerMinute(), DirectorSettings::positiveInt),
            read(object, "maxBlocksPerCommand", defaults.maxBlocksPerCommand(), DirectorSettings::positiveInt),
            read(object, "maxSummonPerCommand", defaults.maxSummonPerCommand(), DirectorSettings::positiveInt),
            read(object, "maxActiveEntities", defaults.maxActiveEntities(), DirectorSettings::positiveInt),
            read(object, "undoDepth", defaults.undoDepth(), DirectorSettings::positiveInt),
            read(object, "commandTimeoutMs", defaults.commandTimeoutMs(), DirectorSettings::positiveInt),
            read(object, "allowModCommands", defaults.allowModCommands(), DirectorSettings::bool),
            read(object, "summonDenylist", defaults.summonDenylist(), DirectorSettings::strings),
            read(object, "featureDenylist", defaults.featureDenylist(), DirectorSettings::strings),
            read(object, "smallStructures", defaults.smallStructures(), DirectorSettings::strings));
    }

    private static <T> T read(JsonObject object, String key, T fallback, Function<JsonElement, T> parse) {
        T result = fallback;
        JsonElement element = object.get(key);
        if (element != null) {
            try {
                result = parse.apply(element);
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) {
                LOG.warn("配置 director.{} 无效，已使用默认值", key);
            }
        }
        JsonUtil.putNullable(object, key, result);
        return result;
    }

    private static boolean bool(JsonElement value) {
        if (!value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
        return value.getAsBoolean();
    }

    private static int positiveInt(JsonElement value) {
        if (!value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        int number = value.getAsBigDecimal().intValueExact();
        if (number <= 0) throw new IllegalArgumentException();
        return number;
    }

    private static String nonBlankString(JsonElement value) {
        if (!value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) throw new IllegalArgumentException();
        return value.getAsString();
    }

    private static List<String> strings(JsonElement value) {
        return StreamSupport.stream(value.getAsJsonArray().spliterator(), false)
            .map(DirectorSettings::nonBlankString).toList();
    }
}

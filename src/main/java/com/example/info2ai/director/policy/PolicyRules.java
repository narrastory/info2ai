package com.example.info2ai.director.policy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 规格 §6 的根指令与子规则。 */
public final class PolicyRules {
    private PolicyRules() { }

    public static final Map<String, String> DENIED_ROOTS = deniedRoots();
    public static final Set<String> ALLOWED_ROOTS = Set.of(
        "bossbar", "enchant", "experience", "xp", "give", "help", "item", "list", "loot",
        "me", "msg", "tell", "w", "teammsg", "tm", "say", "tellraw", "particle", "playsound",
        "stopsound", "random", "return", "scoreboard", "seed", "setblock", "setworldspawn",
        "spawnpoint", "tag", "team", "time", "title", "trigger", "weather"
    );

    static final Set<String> ENTITY_NBT_KEYS = Set.of("Pos", "Motion", "Rotation", "Health", "Dimension", "Passengers", "RootVehicle");
    static final String MAX_HEALTH = "minecraft:generic.max_health";
    static final String HEALTH_BOOST = "minecraft:health_boost";
    static final String UNKNOWN_ROOT = "非原版指令默认禁止";
    static final String DATA_REASON = "不能改实体的位置、速度、朝向、生命值";
    static final String FUNCTION_REASON = "函数无法被逐条检查";
    static final String SUMMON_REASON = "该实体已被禁止生成";

    private static Map<String, String> deniedRoots() {
        Map<String, String> rules = new HashMap<>();
        deny(rules, "不能用指令直接造成伤害", "damage");
        deny(rules, "会改变存档的数据包", "datapack");
        deny(rules, "调试指令", "debug");
        deny(rules, "会改变存档设置", "defaultgamemode", "difficulty");
        deny(rules, "会让区块常驻内存", "forceload");
        deny(rules, FUNCTION_REASON, "function");
        deny(rules, "不能切换玩家的游戏模式", "gamemode");
        deny(rules, "会改变存档规则", "gamerule");
        deny(rules, "性能分析指令", "jfr", "perf");
        deny(rules, "不能用指令直接杀死生物", "kill");
        deny(rules, "会冻结游戏数秒；请用世界静态数据（mc_query_world）", "locate");
        deny(rules, "会把单人世界开放到局域网", "publish");
        deny(rules, "会重载数据包", "reload");
        deny(rules, "骑乘会移动实体", "ride");
        deny(rules, "延迟执行无法被检查", "schedule");
        deny(rules, "会移动玩家视角/位置", "spectate");
        deny(rules, "会传送实体", "spreadplayers");
        deny(rules, "不能传送任何实体", "teleport", "tp");
        deny(rules, "会冻结或加速世界", "tick");
        deny(rules, "可能把玩家困住", "worldborder");
        deny(rules, "服务器管理指令", "stop", "save-all", "save-off", "save-on", "op", "deop", "ban", "ban-ip",
            "banlist", "pardon", "pardon-ip", "kick", "whitelist", "setidletimeout", "transfer");
        deny(rules, "开发者指令", "test", "raid", "debugpath", "debugmobspawning", "warden_spawn_tracker",
            "spawn_armor_trims", "serverpack", "debugconfig", "chase");
        return Map.copyOf(rules);
    }

    private static void deny(Map<String, String> map, String reason, String... roots) {
        for (String root : roots) map.put(root, reason);
    }
}

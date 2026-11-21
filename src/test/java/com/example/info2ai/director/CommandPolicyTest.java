package com.example.info2ai.director;

import com.example.info2ai.director.policy.CommandPolicy;
import com.example.info2ai.director.policy.CommandShape;
import com.example.info2ai.director.policy.CommandShape.BlockPosition;
import com.example.info2ai.director.policy.CommandShape.Segment;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class CommandPolicyTest {
    private static final CommandPolicy POLICY = new CommandPolicy(DirectorSettings.defaults());

    @Test
    public void refusesEveryDeniedRootWithItsDocumentedReason() {
        Map.ofEntries(
            Map.entry("不能用指令直接造成伤害", "damage"),
            Map.entry("会改变存档的数据包", "datapack"),
            Map.entry("调试指令", "debug"),
            Map.entry("会改变存档设置", "defaultgamemode difficulty"),
            Map.entry("会让区块常驻内存", "forceload"),
            Map.entry("函数无法被逐条检查", "function"),
            Map.entry("不能切换玩家的游戏模式", "gamemode"),
            Map.entry("会改变存档规则", "gamerule"),
            Map.entry("性能分析指令", "jfr perf"),
            Map.entry("不能用指令直接杀死生物", "kill"),
            Map.entry("会冻结游戏数秒；请用世界静态数据（mc_query_world）", "locate"),
            Map.entry("会把单人世界开放到局域网", "publish"),
            Map.entry("会重载数据包", "reload"),
            Map.entry("骑乘会移动实体", "ride"),
            Map.entry("延迟执行无法被检查", "schedule"),
            Map.entry("会移动玩家视角/位置", "spectate"),
            Map.entry("会传送实体", "spreadplayers"),
            Map.entry("不能传送任何实体", "teleport tp minecraft:tp"),
            Map.entry("会冻结或加速世界", "tick"),
            Map.entry("可能把玩家困住", "worldborder"),
            Map.entry("服务器管理指令", "stop save-all save-off save-on op deop ban ban-ip banlist pardon pardon-ip kick whitelist setidletimeout transfer"),
            Map.entry("开发者指令", "test raid debugpath debugmobspawning warden_spawn_tracker spawn_armor_trims serverpack debugconfig chase"),
            Map.entry("非原版指令默认禁止", "mod:custom")
        ).forEach((reason, roots) -> {
            for (String name : roots.split(" ")) {
                var decision = POLICY.evaluate(shape(root(name)));
                assertFalse(name, decision.allowed());
                assertEquals(name, reason, decision.reason());
            }
        });
    }

    @Test
    public void enforcesProgressAndClearRules() {
        denied("P-ADV", root("advancement revoke <targets> everything"));
        allowed(root("advancement grant <targets> everything"));
        denied("P-REC", root("recipe take <targets> <recipe>"));
        allowed(root("recipe give <targets> <recipe>"));
        denied("P-CLR", root("clear <targets>"));
        denied("P-CLR", root("clear <targets> <item>", Map.of("item", "minecraft:bread"), Map.of()));
        denied("P-CLR", clear(0));
        allowed(clear(2));
    }

    @Test
    public void preservesMaxHealthThroughAttributesAndEffects() {
        String health = "minecraft:generic.max_health";
        denied("P-ATT", attribute("base set <value>", health, Map.of("value", 30.0)));
        denied("P-ATT", attribute("modifier remove <id>", health, Map.of()));
        denied("P-ATT", attribute("modifier add <id> <value> add_value", health, Map.of("value", -2.0)));
        denied("P-ATT", attribute("modifier add <id> <value> add_multiplied_total", health, Map.of("value", 2.0)));
        allowed(attribute("modifier add <id> <value> add_value", health, Map.of("value", 2.0)));
        allowed(attribute("base get", health, Map.of()));
        allowed(attribute("base set <value>", "minecraft:generic.armor", Map.of("value", 2.0)));
        denied("P-EFC", root("effect clear <targets> <effect>", Map.of("effect", "health_boost"), Map.of()));
        allowed(root("effect clear <targets>"));
        allowed(root("effect clear <targets> <effect>", Map.of("effect", "poison"), Map.of()));
        allowed(root("effect give <targets> <effect>", Map.of("effect", "instant_damage"), Map.of()));
    }

    @Test
    public void checksDataTargetsAndRootFilters() {
        allowed(root("data merge entity <target> <nbt>", Map.of(), Map.of("nbt", Set.of("CustomName"))));
        denied("P-DAT", root("data merge entity <target> <nbt>", Map.of(), Map.of("nbt", Set.of("Pos", "CustomName"))));
        for (String key : List.of("Pos", "Motion", "Rotation", "Health", "Dimension", "Passengers", "RootVehicle")) {
            denied("P-DAT", root("data remove entity <target> <targetPath>", Map.of("targetPath", key + "[0]"), Map.of()));
        }
        denied("P-DAT", root("data modify entity <target> <targetPath> set value <value>",
            Map.of("targetPath", "\"Motion\"[1]"), Map.of()));
        denied("P-DAT", root("data modify entity <target> <targetPath> set value <value>",
            Map.of("targetPath", "'Motion'[1]"), Map.of()));
        denied("P-DAT", root("data modify entity <target> <targetPath> merge value <value>",
            Map.of("targetPath", "{}"), Map.of()));
        allowed(root("data modify entity <target> <targetPath> set from entity <source> <sourcePath>",
            Map.of("targetPath", "CustomName", "sourcePath", "Pos"), Map.of()));
        allowed(root("data get entity <target> <path>", Map.of("path", "Pos"), Map.of()));
        denied("P-DAT", root("data remove entity <target> <path>", Map.of("path", "Pos"), Map.of()));
        allowed(root("data remove entity <target> <path>", Map.of("path", "CustomName"), Map.of()));
    }

    @Test
    public void checksEveryNestedRootAndExecuteClause() {
        denied("P-ROOT", root("execute as <targets>"), clause("at <targets>"), clause("run"), root("kill <targets>"));
        denied("P-ROOT", root("execute at <targets>"), clause("run"), root("execute as <targets>"), clause("run"), root("tp <targets> <location>"));
        denied("P-STO", root("execute store result entity <target> <path> float <scale>",
            Map.of("path", "Health"), Map.of()), clause("run"), root("random value <range>"));
        denied("P-STO", root("execute as <targets>"),
            segment(false, "store result entity <target> <path> double <scale>", Map.of("path", "Motion[1]"), Map.of()),
            clause("run"), root("random value <range>"));
        denied("P-STO", root("execute store result entity <target> <path> int <scale>",
            Map.of("path", "'Health'"), Map.of()), clause("run"), root("random value <range>"));
        allowed(root("execute store result entity <target> <path> int <scale>",
            Map.of("path", "Age"), Map.of()), clause("run"), root("random value <range>"));
        denied("P-FN", root("execute if function <name>"));
        denied("P-FN", root("execute as <targets>"), clause("unless function <name>"));
        allowed(root("execute if entity <entities>"), clause("run"), root("say <message>"));
        denied("P-SUM", root("execute summon <entity>", Map.of("entity", "minecraft:wither"), Map.of()),
            clause("run"), root("say <message>"));
        denied("P-SUM", root("execute as <targets>"),
            segment(false, "summon <entity>", Map.of("entity", "ender_dragon"), Map.of()),
            clause("run"), root("say <message>"));
        allowed(root("execute summon <entity>", Map.of("entity", "pig"), Map.of()), clause("run"), root("say <message>"));
        denied("P-ROOT", root("return run"), root("tp <targets> <location>"));
    }

    @Test
    public void checksWorldgenAndSummonLists() {
        denied("P-FEAT", place("feature", "ore_iron"));
        denied("P-FEAT", place("feature", "minecraft:ore_diamond"));
        allowed(place("feature", "minecraft:oak"));
        denied("P-PLC", place("jigsaw", "minecraft:village/plains/town_centers"));
        denied("P-PLC", place("structure", "minecraft:village_plains"));
        allowed(place("structure", "igloo"));
        allowed(place("template", "minecraft:village/plains/houses/plains_small_house_1"));
        denied("P-SUM", root("summon <entity>", Map.of("entity", "wither"), Map.of()));
        allowed(root("summon <entity>", Map.of("entity", "minecraft:zombie"), Map.of()));
    }

    @Test
    public void computesInclusiveVolumeForEachRegionCommandWithoutIntegerOverflow() {
        for (String name : List.of("fill", "clone", "fillbiome")) {
            var atLimit = region(name, new BlockPosition(15, 15, 15));
            assertEquals(Long.valueOf(4096), POLICY.volume(shape(atLimit)));
            allowed(atLimit);
            denied("P-VOL", region(name, new BlockPosition(16, 15, 15)));
        }
        var huge = root("fill <from> <to> <block>", Map.of(), Map.of(
            "from", new BlockPosition(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
            "to", new BlockPosition(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)));
        denied("P-VOL", huge);
    }

    @Test
    public void usesCustomLimitsAndListsWithoutExemptingDeniedVanillaRoots() {
        var settings = new DirectorSettings(true, "伙伴", 2, 10, 120, 64, 8, 32, 20, 3000, true,
            List.of("minecraft:pig"), List.of("example:rare_*"), List.of("minecraft:swamp_hut"));
        var policy = new CommandPolicy(settings);
        assertTrue(policy.evaluate(shape(root("example:custom"))).allowed());
        assertFalse(policy.evaluate(shape(root("tp"))).allowed());
        assertFalse(policy.evaluate(shape(root("summon <entity>", Map.of("entity", "pig"), Map.of()))).allowed());
        assertTrue(policy.evaluate(shape(root("summon <entity>", Map.of("entity", "wither"), Map.of()))).allowed());
        assertFalse(policy.evaluate(shape(place("feature", "example:rare_tree"))).allowed());
        assertTrue(policy.evaluate(shape(place("feature", "example:other_tree"))).allowed());
        assertFalse(policy.evaluate(shape(place("structure", "igloo"))).allowed());
        assertFalse(policy.evaluate(shape(region("fill", new BlockPosition(4, 4, 4)))).allowed());
    }

    @Test
    public void copiesResolvedNestedValuesBeforePolicyUsesThem() {
        var keys = new ArrayList<>(List.of("Health"));
        var command = new ArrayList<>(List.of(root("data merge entity <target> <nbt>", Map.of(), Map.of("nbt", keys))));
        var shape = new CommandShape("data merge", command);
        keys.clear();
        command.clear();
        assertEquals("P-DAT", POLICY.evaluate(shape).rule());
    }

    private static Segment clear(int amount) {
        return root("clear <targets> <item> <maxCount>", Map.of("item", "minecraft:bread"), Map.of("maxCount", amount));
    }

    private static Segment attribute(String path, String id, Map<String, Object> resolved) {
        return root("attribute <target> <attribute> " + path, Map.of("attribute", id), resolved);
    }

    private static Segment place(String kind, String id) {
        return root("place " + kind + " <" + kind + ">", Map.of(kind, id), Map.of());
    }

    private static Segment region(String name, BlockPosition end) {
        String from = name.equals("clone") ? "begin" : "from";
        String to = name.equals("clone") ? "end" : "to";
        return root(name + " <" + from + "> <" + to + ">", Map.of(),
            Map.of(from, new BlockPosition(0, 0, 0), to, end));
    }

    private static CommandShape shape(Segment... segments) {
        return new CommandShape("", List.of(segments));
    }

    private static void allowed(Segment... segments) {
        var result = POLICY.evaluate(shape(segments));
        assertTrue(result.reason(), result.allowed());
    }

    private static void denied(String rule, Segment... segments) {
        var result = POLICY.evaluate(shape(segments));
        assertFalse(result.allowed());
        assertEquals(rule, result.rule());
    }

    private static Segment root(String path) {
        return root(path, Map.of(), Map.of());
    }

    private static Segment root(String path, Map<String, String> raw, Map<String, Object> resolved) {
        return segment(true, path, raw, resolved);
    }

    private static Segment clause(String path) {
        return segment(false, path, Map.of(), Map.of());
    }

    private static Segment segment(boolean root, String path, Map<String, String> raw, Map<String, Object> resolved) {
        return new Segment(root, List.of(path.split(" ")), raw, resolved);
    }
}

package com.example.info2ai.director;

import com.example.info2ai.api.DirectorRequests.*;
import com.example.info2ai.director.log.ActionRecord;
import com.example.info2ai.director.policy.PolicyDecision;
import com.example.info2ai.model.JsonModel;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DirectorJsonTest {
    @Test
    public void serializesRequestsWithTheWireFieldNames() {
        assertJson(new RunRequest("/say hi", "打招呼", null),
            """
            {"command":"/say hi","intent":"打招呼","visibility":"signed"}
            """);
        assertJson(new CheckRequest("say hi"), "{\"command\":\"say hi\"}");
        assertJson(new UndoRequest(17L, false), "{\"action_id\":17,\"last\":false}");
        assertJson(new UndoRequest(null, true), "{\"action_id\":null,\"last\":true}");
    }

    @Test
    public void includesNullsAndNestedValuesInRunResult() {
        var changes = new Changes(1, List.of(new SpawnedEntity("id", "minecraft:pig", null, List.of(1.0, 2.0, 3.0))),
            List.of(new InventoryChange(4, null, "minecraft:poppy x1")), List.of());
        var guard = new GuardResult(true, List.of(new GuardViolation("G-HAZ-FLOOR", "不能挖空玩家脚下的支撑", List.of(1, 2, 3))),
            List.of(new Veto("damage", "minecraft:cow", 3.0)));
        assertJson(new RunResult(RunStatus.ROLLED_BACK, 17L, true, 2, 3, List.of("反馈"), List.of("错误"),
                null, PolicyDecision.allow(), guard, changes, List.of("effects"), false, new RateStatus(9, 51), 12345L),
            """
            {"status":"rolled_back","action_id":17,"success":true,"result":2,"calls":3,
             "output":["反馈"],"errors":["错误"],"parse_error":null,"policy":{"allowed":true,"rule":null,"reason":null},
             "guard":{"rolled_back":true,"violations":[{"rule":"G-HAZ-FLOOR","reason":"不能挖空玩家脚下的支撑","pos":[1,2,3]}],
                      "vetoes":[{"kind":"damage","type":"minecraft:cow","amount":3.0}]},
             "changes":{"blocks":1,"spawned":[{"uuid":"id","type":"minecraft:pig","name":null,"pos":[1.0,2.0,3.0]}],
                        "inventory":[{"slot":4,"before":null,"after":"minecraft:poppy x1"}],"markers":[]},
             "not_reverted":["effects"],"undoable":false,"rate":{"remaining":9,"reset_in_s":51},"seq":12345}
            """);
        assertJson(new RunResult(RunStatus.PARSE_ERROR, null, false, 0, 0, List.of(), List.of(),
                new ParseError("语法错误", 4, "give<--[此处]"), null, GuardResult.EMPTY, Changes.EMPTY, List.of(), false, new RateStatus(10, 0), null),
            """
            {"status":"parse_error","action_id":null,"success":false,"result":0,"calls":0,"output":[],"errors":[],
             "parse_error":{"message":"语法错误","cursor":4,"context":"give<--[此处]"},"policy":null,
             "guard":{"rolled_back":false,"violations":[],"vetoes":[]},
             "changes":{"blocks":0,"spawned":[],"inventory":[],"markers":[]},"not_reverted":[],
             "undoable":false,"rate":{"remaining":10,"reset_in_s":0},"seq":null}
            """);
    }

    @Test
    public void serializesCheckAndHelpResponses() {
        assertJson(new CheckResult(CheckStatus.OK, null, PolicyDecision.allow(), List.of("execute", "summon"), null),
            """
            {"status":"ok","parse_error":null,"policy":{"allowed":true,"rule":null,"reason":null},"roots":["execute","summon"],"volume":null}
            """);
        assertJson(new HelpResult("give", true, null, List.of("give <targets> <item>"), "说明"),
            """
            {"command":"give","allowed":true,"policy_note":null,"usage":["give <targets> <item>"],"notes":"说明"}
            """);
        assertJson(new HelpResult(null, null, null, List.of(), "坐标说明"),
            """
            {"command":null,"allowed":null,"policy_note":null,"usage":[],"notes":"坐标说明"}
            """);
        assertJson(new HelpIndex(List.of(new CommandEntry("give", "给予物品")),
            List.of(new CommandEntry("tp", "不能传送任何实体")), List.of("guide")),
            """
            {"allowed":[{"command":"give","description":"给予物品"}],
             "denied":[{"command":"tp","description":"不能传送任何实体"}],"topics":["guide"]}
            """);
    }

    @Test
    public void serializesLookupWithKindSpecificFields() {
        assertJson(new LookupResult("item", "花", 2, true,
            List.of(new LookupEntry("minecraft:poppy", "虞美人", "虞美人", null, null))),
            """
            {"kind":"item","q":"花","total":2,"truncated":true,"results":[{"id":"minecraft:poppy","zh":"虞美人","name":"虞美人"}]}
            """);
        assertJson(new LookupEntry("minecraft:igloo", null, "Igloo", null, true),
            """
            {"id":"minecraft:igloo","zh":null,"name":"Igloo","place_allowed":true}
            """);
        assertJson(new LookupEntry("minecraft:wither", "凋零", "凋零", false, null),
            """
            {"id":"minecraft:wither","zh":"凋零","name":"凋零","summon_allowed":false}
            """);
        assertJson(new LookupKinds(List.of("block", "item")), "{\"kinds\":[\"block\",\"item\"]}");
    }

    @Test
    public void serializesUndoActionsAndStatus() {
        assertJson(new UndoResult(UndoStatus.PARTIAL, 17L, 12, 1, 1, List.of(), List.of("minecraft:poppy x1"),
            List.of("effects"), 12350L),
            """
            {"status":"partial","action_id":17,"restored_blocks":12,"skipped_blocks":1,"removed_entities":1,
             "returned_items":[],"reclaimed_items":["minecraft:poppy x1"],"not_reverted":["effects"],"seq":12350}
            """);
        var action = new ActionRecord(17, 1762501234123L, RunStatus.EXECUTED, "问候", "say 你好",
            Visibility.SIGNED, Changes.EMPTY, List.of(), false);
        assertJson(new ActionsResult(List.of(action.summary())), """
            {"actions":[{"action_id":17,"t":1762501234123,"status":"executed","intent":"问候","command":"say 你好",
                        "visibility":"signed","blocks":0,"spawned":0,"undoable":false,"undone":false}]}
            """);
        assertJson(new StatusResult(true, true, new RateStatus(9, 51), new Limits(10, 4096, 8, 32), 3, 5, List.of("kill", "tp")),
            """
            {"enabled":true,"available":true,"rate":{"remaining":9,"reset_in_s":51},
             "limits":{"per_minute":10,"blocks_per_command":4096,"summon_per_command":8,"active_entities":32},
             "active_entities":3,"markers":5,"denied_roots":["kill","tp"]}
            """);
    }

    @Test
    public void capsReportedSpawnsWithoutReducingTheActionSummaryTotal() {
        var source = new ArrayList<SpawnedEntity>();
        for (int i = 0; i < 17; i++) {
            source.add(new SpawnedEntity("id-" + i, "minecraft:pig", null, List.of(1.0, 2.0, 3.0)));
        }
        var changes = new Changes(0, source, List.of(), List.of());
        var action = new ActionRecord(17, 0, RunStatus.EXECUTED, "问候", "summon pig", Visibility.SIGNED,
            changes, List.of(), true);
        source.clear();
        assertEquals(16, changes.toJson().getAsJsonArray("spawned").size());
        assertEquals(17, action.summary().spawned());
    }

    private static void assertJson(JsonModel model, String expected) {
        assertEquals(JsonParser.parseString(expected), model.toJson());
    }
}

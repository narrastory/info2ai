package com.example.info2ai.api;

import com.example.info2ai.director.policy.PolicyDecision;
import com.example.info2ai.model.Detail;
import com.example.info2ai.model.JsonModel;
import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

/** 导演 HTTP 契约。可空字段显式写入 JSON。 */
public final class DirectorRequests {
    private DirectorRequests() { }

    public enum RunStatus { EXECUTED, PARSE_ERROR, REJECTED, ROLLED_BACK, FAILED, TIMEOUT }
    public enum CheckStatus { OK, PARSE_ERROR, REJECTED }
    public enum UndoStatus { UNDONE, PARTIAL, NOT_FOUND, EXPIRED }
    public enum Visibility { SIGNED, NATURAL, AMBIENT }

    public record RunRequest(String command, String intent, Visibility visibility) implements Payload {
        public RunRequest {
            if (visibility == null) visibility = Visibility.SIGNED;
        }

        public JsonObject toJson() {
            return json("command", command, "intent", intent, "visibility", visibility);
        }
    }

    public record CheckRequest(String command) implements Payload {
        public JsonObject toJson() {
            return json("command", command);
        }
    }

    public record UndoRequest(Long actionId, boolean last) implements Payload {
        public JsonObject toJson() {
            return json("action_id", actionId, "last", last);
        }
    }

    public record ParseError(String message, int cursor, String context) implements Payload {
        public JsonObject toJson() {
            return json("message", message, "cursor", cursor, "context", context);
        }
    }

    public record RateStatus(int remaining, int resetInSeconds) implements Payload {
        public JsonObject toJson() {
            return json("remaining", remaining, "reset_in_s", resetInSeconds);
        }
    }

    public record GuardViolation(String rule, String reason, List<Integer> pos) implements Payload {
        public GuardViolation {
            if (pos != null) pos = List.copyOf(pos);
        }

        public JsonObject toJson() {
            return json("rule", rule, "reason", reason, "pos", pos);
        }
    }

    public record Veto(String kind, String type, Double amount) implements Payload {
        public JsonObject toJson() {
            return json("kind", kind, "type", type, "amount", amount);
        }
    }

    public record GuardResult(boolean rolledBack, List<GuardViolation> violations, List<Veto> vetoes) implements Payload {
        public static final GuardResult EMPTY = new GuardResult(false, List.of(), List.of());

        public GuardResult {
            violations = List.copyOf(violations);
            vetoes = List.copyOf(vetoes);
        }

        public JsonObject toJson() {
            return json("rolled_back", rolledBack, "violations", violations, "vetoes", vetoes);
        }
    }

    public record SpawnedEntity(String uuid, String type, String name, List<Double> pos) implements Payload {
        public SpawnedEntity {
            pos = List.copyOf(pos);
        }

        public JsonObject toJson() {
            return json("uuid", uuid, "type", type, "name", name, "pos", pos);
        }
    }

    public record InventoryChange(int slot, String before, String after) implements Payload {
        public JsonObject toJson() {
            return json("slot", slot, "before", before, "after", after);
        }
    }

    public record Marker(String block, List<Integer> pos) implements Payload {
        public Marker {
            pos = List.copyOf(pos);
        }

        public JsonObject toJson() {
            return json("block", block, "pos", pos);
        }
    }

    public record Changes(int blocks, List<SpawnedEntity> spawned, List<InventoryChange> inventory,
                          List<Marker> markers) implements Payload {
        public static final Changes EMPTY = new Changes(0, List.of(), List.of(), List.of());

        public Changes {
            spawned = List.copyOf(spawned);
            inventory = List.copyOf(inventory);
            markers = List.copyOf(markers);
        }

        public JsonObject toJson() {
            return json("blocks", blocks, "spawned", spawned.subList(0, Math.min(spawned.size(), 16)),
                "inventory", inventory, "markers", markers);
        }
    }

    public record RunResult(RunStatus status, Long actionId, boolean success, int result, int calls,
                            List<String> output, List<String> errors, ParseError parseError, PolicyDecision policy,
                            GuardResult guard, Changes changes, List<String> notReverted,
                            boolean undoable, RateStatus rate, Long seq) implements Payload {
        public RunResult {
            output = List.copyOf(output);
            errors = List.copyOf(errors);
            notReverted = List.copyOf(notReverted);
        }

        public JsonObject toJson() {
            return json("status", status, "action_id", actionId, "success", success, "result", result, "calls", calls,
                "output", output, "errors", errors, "parse_error", parseError, "policy", policy, "guard", guard,
                "changes", changes, "not_reverted", notReverted, "undoable", undoable, "rate", rate, "seq", seq);
        }
    }

    public record CheckResult(CheckStatus status, ParseError parseError, PolicyDecision policy, List<String> roots,
                              Long volume) implements Payload {
        public CheckResult {
            roots = List.copyOf(roots);
        }

        public JsonObject toJson() {
            return json("status", status, "parse_error", parseError, "policy", policy, "roots", roots, "volume", volume);
        }
    }

    public record CommandEntry(String command, String description) implements Payload {
        public JsonObject toJson() {
            return json("command", command, "description", description);
        }
    }

    public record HelpIndex(List<CommandEntry> allowed, List<CommandEntry> denied, List<String> topics) implements Payload {
        public HelpIndex {
            allowed = List.copyOf(allowed);
            denied = List.copyOf(denied);
            topics = List.copyOf(topics);
        }

        public JsonObject toJson() {
            return json("allowed", allowed, "denied", denied, "topics", topics);
        }
    }

    public record HelpResult(String command, Boolean allowed, String policyNote, List<String> usage,
                             String notes, HelpIndex index) implements Payload {
        public HelpResult(String command, Boolean allowed, String policyNote, List<String> usage, String notes) {
            this(command, allowed, policyNote, usage, notes, null);
        }

        public HelpResult {
            usage = List.copyOf(usage);
        }

        public JsonObject toJson() {
            if (index != null) return index.toJson();
            return json("command", command, "allowed", allowed, "policy_note", policyNote, "usage", usage, "notes", notes);
        }
    }

    public record LookupEntry(String id, String zh, String name, Boolean summonAllowed, Boolean placeAllowed)
        implements Payload {
        public JsonObject toJson() {
            JsonObject result = json("id", id, "zh", zh, "name", name);
            if (summonAllowed != null) result.addProperty("summon_allowed", summonAllowed);
            if (placeAllowed != null) result.addProperty("place_allowed", placeAllowed);
            return result;
        }
    }

    public record LookupKinds(List<String> kinds) implements Payload {
        public LookupKinds {
            kinds = List.copyOf(kinds);
        }

        public JsonObject toJson() {
            return json("kinds", kinds);
        }
    }

    public record LookupResult(String kind, String query, int total, boolean truncated, List<LookupEntry> results,
                               LookupKinds index) implements Payload {
        public LookupResult(String kind, String query, int total, boolean truncated, List<LookupEntry> results) {
            this(kind, query, total, truncated, results, null);
        }

        public LookupResult {
            results = List.copyOf(results);
        }

        public JsonObject toJson() {
            if (index != null) return index.toJson();
            return json("kind", kind, "q", query, "total", total, "truncated", truncated, "results", results);
        }
    }

    public record UndoResult(UndoStatus status, Long actionId, int restoredBlocks, int skippedBlocks, int removedEntities,
                             List<String> returnedItems, List<String> reclaimedItems, List<String> notReverted, Long seq)
        implements Payload {
        public UndoResult {
            returnedItems = List.copyOf(returnedItems);
            reclaimedItems = List.copyOf(reclaimedItems);
            notReverted = List.copyOf(notReverted);
        }

        public JsonObject toJson() {
            return json("status", status, "action_id", actionId, "restored_blocks", restoredBlocks,
                "skipped_blocks", skippedBlocks, "removed_entities", removedEntities, "returned_items", returnedItems,
                "reclaimed_items", reclaimedItems, "not_reverted", notReverted, "seq", seq);
        }
    }

    public record ActionSummary(long actionId, long t, RunStatus status, String intent, String command,
                                Visibility visibility, int blocks, int spawned, boolean undoable, boolean undone)
        implements Payload {
        public JsonObject toJson() {
            return json("action_id", actionId, "t", t, "status", status, "intent", intent, "command", command,
                "visibility", visibility, "blocks", blocks, "spawned", spawned, "undoable", undoable, "undone", undone);
        }
    }

    public record ActionsResult(List<ActionSummary> actions) implements Payload {
        public ActionsResult {
            actions = List.copyOf(actions);
        }

        public JsonObject toJson() {
            return json("actions", actions);
        }
    }

    public record Limits(int perMinute, int blocksPerCommand, int summonPerCommand, int activeEntities) implements Payload {
        public JsonObject toJson() {
            return json("per_minute", perMinute, "blocks_per_command", blocksPerCommand,
                "summon_per_command", summonPerCommand, "active_entities", activeEntities);
        }
    }

    public record StatusResult(boolean enabled, boolean available, RateStatus rate, Limits limits, int activeEntities,
                               int markers, List<String> deniedRoots, List<String> guardMissing) implements Payload {
        public StatusResult(boolean enabled, boolean available, RateStatus rate, Limits limits, int activeEntities,
                            int markers, List<String> deniedRoots) {
            this(enabled, available, rate, limits, activeEntities, markers, deniedRoots, List.of());
        }

        public StatusResult {
            deniedRoots = List.copyOf(deniedRoots);
            guardMissing = List.copyOf(guardMissing);
        }

        public JsonObject toJson() {
            JsonObject result = json("enabled", enabled, "available", available, "rate", rate, "limits", limits,
                "active_entities", activeEntities, "markers", markers, "denied_roots", deniedRoots);
            if (!guardMissing.isEmpty()) JsonUtil.putNullable(result, "guard_missing", guardMissing);
            return result;
        }
    }

    private interface Payload extends JsonModel {
        JsonObject toJson();

        @Override
        default JsonObject toJson(Detail detail) {
            return toJson();
        }
    }

    private static JsonObject json(Object... pairs) {
        JsonObject object = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value instanceof Enum<?> option) value = option.name().toLowerCase(Locale.ROOT);
            JsonUtil.putNullable(object, (String) pairs[i], value);
        }
        return object;
    }
}

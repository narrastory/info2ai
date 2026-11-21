package com.example.info2ai.director.policy;

import com.example.info2ai.director.DirectorSettings;
import com.example.info2ai.director.policy.CommandShape.BlockPosition;
import com.example.info2ai.director.policy.CommandShape.Segment;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.example.info2ai.director.policy.PolicyDecision.allow;
import static com.example.info2ai.director.policy.PolicyDecision.deny;

public final class CommandPolicy {
    private static final Pattern ROOT_KEY = Pattern.compile("^(['\"])(.*?)\\1|^([^.\\[\\]{}'\"]+)");
    private final DirectorSettings settings;
    private final List<Pattern> deniedFeatures;
    private final Map<String, Function<Segment, PolicyDecision>> rules = Map.ofEntries(
        Map.entry("advancement", segment -> segment.path().contains("revoke")
            ? deny("P-ADV", "不能撤销玩家进度") : allow()),
        Map.entry("recipe", segment -> segment.path().contains("take")
            ? deny("P-REC", "不能撤销玩家配方") : allow()),
        Map.entry("clear", CommandPolicy::clear),
        Map.entry("attribute", CommandPolicy::attribute),
        Map.entry("effect", CommandPolicy::effect),
        Map.entry("data", CommandPolicy::data),
        Map.entry("place", this::place),
        Map.entry("summon", this::summon),
        Map.entry("execute", CommandPolicy::executeClause),
        Map.entry("fill", this::region),
        Map.entry("clone", this::region),
        Map.entry("fillbiome", this::region)
    );

    public CommandPolicy(DirectorSettings settings) {
        this.settings = settings;
        deniedFeatures = settings.featureDenylist().stream().map(CommandPolicy::glob).toList();
    }

    public PolicyDecision evaluate(CommandShape shape) {
        String root = "";
        for (Segment segment : shape.segments()) {
            if (segment.path().isEmpty()) continue;
            PolicyDecision result;
            if (segment.rootCommand()) {
                root = canonicalRoot(segment);
                result = evaluateRoot(root, segment);
            } else if (root.equals("execute")) {
                result = executeClause(segment);
            } else {
                continue;
            }
            if (!result.allowed()) return result;
            if (root.equals("execute") && clauseStartsWith(segment, "summon")) {
                result = summon(segment);
                if (!result.allowed()) return result;
            }
        }
        return allow();
    }

    private PolicyDecision evaluateRoot(String root, Segment segment) {
        String reason = PolicyRules.DENIED_ROOTS.get(root);
        if (reason != null) return deny("P-ROOT", reason);
        var rule = rules.get(root);
        if (rule != null) return rule.apply(segment);
        if (settings.allowModCommands() || PolicyRules.ALLOWED_ROOTS.contains(root)) return allow();
        return deny("P-ROOT", PolicyRules.UNKNOWN_ROOT);
    }

    public String rootDenial(String root) {
        if (root.startsWith("minecraft:")) root = root.substring("minecraft:".length());
        var denied = PolicyRules.DENIED_ROOTS.get(root);
        if (denied != null) return denied;
        return settings.allowModCommands() || rules.containsKey(root) || PolicyRules.ALLOWED_ROOTS.contains(root)
            ? null : PolicyRules.UNKNOWN_ROOT;
    }

    private static PolicyDecision clear(Segment segment) {
        if (segment.argsRaw().containsKey("item")
            && segment.argsResolved().get("maxCount") instanceof Integer count && count >= 1) return allow();
        return deny("P-CLR", "clear 必须写明物品和数量，例如 clear @p minecraft:bread 2");
    }

    private static PolicyDecision attribute(Segment segment) {
        if (!PolicyRules.MAX_HEALTH.equals(id(segment, "attribute"))) return allow();
        List<String> path = segment.path();
        if (path.contains("set") || path.contains("remove") || path.contains("add")
            && (!(segment.argsResolved().get("value") instanceof Number value)
                || !Double.isFinite(value.doubleValue()) || value.doubleValue() < 0 || !path.contains("add_value"))) {
            return deny("P-ATT", "不能降低最大生命值；想临时加血量上限请用生命提升效果");
        }
        return allow();
    }

    private static PolicyDecision effect(Segment segment) {
        if (segment.path().contains("clear") && PolicyRules.HEALTH_BOOST.equals(id(segment, "effect"))) {
            return deny("P-EFC", "清除生命提升会直接降低血量");
        }
        return allow();
    }

    private static PolicyDecision data(Segment segment) {
        if (segment.path().size() < 3 || !segment.path().get(2).equals("entity")) return allow();
        boolean unsafe = switch (segment.path().get(1)) {
            case "merge" -> !(segment.argsResolved().get("nbt") instanceof Collection<?> keys)
                || keys.stream().anyMatch(PolicyRules.ENTITY_NBT_KEYS::contains);
            case "modify" -> forbiddenPath(segment.argsRaw().get("targetPath"));
            case "remove" -> forbiddenPath(segment.argsRaw().getOrDefault("path", segment.argsRaw().get("targetPath")));
            default -> false;
        };
        return unsafe ? deny("P-DAT", PolicyRules.DATA_REASON) : allow();
    }

    private static PolicyDecision executeClause(Segment segment) {
        if (segment.path().contains("function")) return deny("P-FN", PolicyRules.FUNCTION_REASON);
        if (clauseStartsWith(segment, "store") && segment.path().contains("entity")
            && forbiddenPath(segment.argsRaw().get("path"))) {
            return deny("P-STO", PolicyRules.DATA_REASON);
        }
        return allow();
    }

    private static boolean clauseStartsWith(Segment segment, String literal) {
        int offset = segment.rootCommand() ? 1 : 0;
        return segment.path().size() > offset && segment.path().get(offset).equals(literal);
    }

    private static boolean forbiddenPath(String path) {
        if (path == null) return true;
        var key = ROOT_KEY.matcher(path);
        if (!key.find()) return true;
        return PolicyRules.ENTITY_NBT_KEYS.contains(key.group(1) == null ? key.group(3) : key.group(2));
    }

    private PolicyDecision summon(Segment segment) {
        return settings.summonDenylist().contains(id(segment, "entity")) ? deny("P-SUM", PolicyRules.SUMMON_REASON) : allow();
    }

    private PolicyDecision place(Segment segment) {
        return switch (segment.path().get(1)) {
            case "feature" -> deniedFeatures.stream().anyMatch(pattern -> pattern.matcher(id(segment, "feature")).matches())
                ? deny("P-FEAT", "矿脉地物绕过方块记录，无法撤销；想放矿请用 fill ... replace minecraft:stone") : allow();
            case "structure" -> settings.smallStructures().contains(id(segment, "structure")) ? allow() : denyStructure();
            case "jigsaw" -> denyStructure();
            default -> allow();
        };
    }

    private PolicyDecision denyStructure() {
        return deny("P-PLC", "只允许放置小型结构：" + String.join("、", settings.smallStructures()));
    }

    private PolicyDecision region(Segment segment) {
        Long volume = regionVolume(segment);
        if (volume == null || volume > settings.maxBlocksPerCommand()) {
            String limit = String.format(Locale.ROOT, "单条最多改动 %d 个方块", settings.maxBlocksPerCommand());
            if (settings.maxBlocksPerCommand() == 4096) limit += "（约 16×16×16）";
            return deny("P-VOL", limit + "，请拆成多条");
        }
        return allow();
    }

    public Long volume(CommandShape shape) {
        return shape.segments().stream().filter(Segment::rootCommand)
            .filter(segment -> !segment.path().isEmpty())
            .filter(segment -> List.of("fill", "clone", "fillbiome").contains(canonicalRoot(segment)))
            .map(CommandPolicy::regionVolume).filter(Objects::nonNull).max(Long::compareTo).orElse(null);
    }

    private static Long regionVolume(Segment segment) {
        boolean clone = canonicalRoot(segment).equals("clone");
        if (!(segment.argsResolved().get(clone ? "begin" : "from") instanceof BlockPosition first)
            || !(segment.argsResolved().get(clone ? "end" : "to") instanceof BlockPosition second)) return null;
        long x = Math.abs((long) first.x() - second.x()) + 1;
        long y = Math.abs((long) first.y() - second.y()) + 1;
        long z = Math.abs((long) first.z() - second.z()) + 1;
        if (x > Long.MAX_VALUE / y || x * y > Long.MAX_VALUE / z) return Long.MAX_VALUE;
        return x * y * z;
    }

    private static String id(Segment segment, String argument) {
        String text = segment.argsResolved().get(argument) instanceof String resolved
            ? resolved : segment.argsRaw().getOrDefault(argument, "");
        return text.contains(":") ? text : "minecraft:" + text;
    }

    private static String canonicalRoot(Segment segment) {
        String root = segment.path().getFirst();
        return root.startsWith("minecraft:") ? root.substring("minecraft:".length()) : root;
    }

    private static Pattern glob(String id) {
        String text = id.contains(":") ? id : "minecraft:" + id;
        return Pattern.compile(String.join(".*",
            Arrays.stream(text.split("\\*", -1)).map(Pattern::quote).toList()));
    }
}

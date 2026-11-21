package com.example.info2ai.director;

import com.example.info2ai.api.DirectorPort;
import com.example.info2ai.api.DirectorRequests.*;
import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.capture.server.SessionSection;
import com.example.info2ai.director.guard.*;
import com.example.info2ai.director.log.ActionLog;
import com.example.info2ai.director.log.ActionRecord;
import com.example.info2ai.director.log.MarkerStore;
import com.example.info2ai.director.catalog.CommandCatalog;
import com.example.info2ai.director.catalog.CommandNotes;
import com.example.info2ai.director.catalog.RegistryCatalog;
import com.example.info2ai.director.policy.CommandPolicy;
import com.example.info2ai.director.policy.CommandShape;
import com.example.info2ai.director.policy.PolicyDecision;
import com.example.info2ai.director.policy.PolicyRules;
import com.example.info2ai.director.policy.ShapeExtractor;
import com.example.info2ai.http.ApiException;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.CommandExecutionContext;
import net.minecraft.command.ReturnValueConsumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 世界修改、命令解析与玩家实体访问均在集成服务端线程完成。 */
public final class DirectorService implements DirectorPort {
    private static final Logger LOG = LoggerFactory.getLogger("info2ai.director");
    private final MinecraftServer server;
    private final Info2AiConfig config;
    private final RateLimiter runs;
    private final RateLimiter checks;
    private final Semaphore inFlight = new Semaphore(1, true);
    private final com.example.info2ai.capture.merge.PerfStats executionPerf = new com.example.info2ai.capture.merge.PerfStats();
    private volatile boolean running = true;
    private volatile boolean playerPresent;
    private volatile boolean playerDead;
    private final List<String> missingGuards;
    private volatile int activeEntities;
    private final LinkedHashMap<Long, UndoData> undoData = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> eventTicks = new LinkedHashMap<>();
    private final ActionLog log;
    private final MarkerStore markers;
    private boolean undoing;
    private volatile Catalogs catalogs;
    private CommandNotes commandNotes;
    private DirectorSettings policySettings;
    private CommandPolicy serverPolicy;

    private DirectorService(MinecraftServer server, Info2AiConfig config) {
        this.server = server;
        this.config = config;
        runs = new RateLimiter(config.director().ratePerMinute());
        checks = new RateLimiter(config.director().checkPerMinute());
        policy(config.director());
        var worldId = SessionSection.worldId(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT));
        log = new ActionLog(config.directory().resolve("info2ai").resolve("director").resolve(worldId));
        markers = new MarkerStore(log);
        rebuildCatalogs();
        // 这些命令类已在 dispatcher 构建时加载。盔甲架可能尚未实例化。
        try {
            for (var type : List.of(net.minecraft.entity.decoration.ArmorStandEntity.class,
                net.minecraft.command.BlockDataObject.class)) {
                Class.forName(type.getName(), false, type.getClassLoader());
            }
        } catch (ClassNotFoundException | LinkageError failure) {
            LOG.error("导演护栏目标无法加载", failure);
        }
        missingGuards = GuardMixinPlugin.missing();
        if (missingGuards.isEmpty()) LOG.info("导演运行时护栏已全部应用");
        else for (String guard : missingGuards) {
            LOG.error("导演护栏未应用：{}", guard);
            Info2AiApi.store().warning("director_guard_missing:" + guard);
        }
    }

    public static void register(Info2AiConfig config) {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (server.isSingleplayer() && Info2AiApi.realCapture()) {
                Info2AiApi.director(new DirectorService(server, config));
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (Info2AiApi.director() instanceof DirectorService service && service.server == server) {
                service.running = false;
                service.log.close();
                Info2AiApi.director(DirectorPort.NONE);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> updatePlayer(server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (Info2AiApi.director() instanceof DirectorService service && service.server == server
                && server.isHost(handler.player.getGameProfile())) {
                service.playerPresent = false;
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(DirectorService::updatePlayer);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success && Info2AiApi.director() instanceof DirectorService service && service.server == server) {
                service.rebuildCatalogs();
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (Info2AiApi.director() instanceof DirectorService service && service.server == world.getServer()
                && !service.undoing && ActionWindow.active() == null
                && entity.getRemovalReason() == net.minecraft.entity.Entity.RemovalReason.DISCARDED) {
                DirectorEvents.gone(entity, "移除");
            }
        });
    }

    private static void updatePlayer(MinecraftServer server) {
        if (Info2AiApi.director() instanceof DirectorService service && service.server == server) {
            ServerPlayerEntity player = service.host();
            service.playerDead = player != null && (player.isDead() || player.isRemoved());
            service.playerPresent = player != null;
        }
    }

    @Override
    public boolean available() {
        return running && playerPresent && config.director().enabled() && missingGuards.isEmpty() && log.ready();
    }

    @Override
    public RunResult run(RunRequest request) {
        checkContext(true);
        long timeout = config.director().commandTimeoutMs();
        acquireRun(timeout);
        CompletableFuture<RunResult> submitted;
        try {
            submitted = server.submit(() -> {
                long start = System.nanoTime();
                // TODO(perf): 首条 list 12.696ms、首次橡树 22.177ms，热态复测低于 5ms。
                // Phase 9 前用冷启动分段测量区分原版地物生成、类加载和导演记录开销。
                try { return execute(request); }
                finally { executionPerf.record((System.nanoTime() - start) / 1000); }
            });
        } catch (RuntimeException failure) {
            inFlight.release();
            throw failure;
        }
        // HTTP 超时不取消已提交的任务，也不提前让下一条修改指令进队列。
        submitted.whenComplete((result, failure) -> inFlight.release());
        try {
            return await(submitted, timeout);
        } catch (TimeoutException waiting) {
            return empty(RunStatus.TIMEOUT, null, null);
        }
    }

    @Override
    public CheckResult check(String command) {
        checkContext(false);
        try {
            return await(server.submit(() -> inspect(command)), config.director().commandTimeoutMs());
        } catch (TimeoutException waiting) {
            throw new ApiException(504, "timeout");
        }
    }

    private CheckResult inspect(String input) {
        ServerPlayerEntity player = requirePlayer(false);
        String command = normalize(input);
        DirectorSettings settings = config.director();
        checks.limit(settings.checkPerMinute());
        long now = nowMs();
        if (!checks.tryAcquire(now)) throw limited(checks, now);

        var source = DirectorSource.create(player, settings, new CapturingOutput());
        try {
            var commandPolicy = policy(settings);
            var parsed = parse(command, source);
            var decision = commandPolicy.evaluate(parsed.shape());
            return new CheckResult(decision.allowed() ? CheckStatus.OK : CheckStatus.REJECTED,
                null, decision, parsed.shape().roots(), commandPolicy.volume(parsed.shape()));
        } catch (CommandSyntaxException invalid) {
            return new CheckResult(CheckStatus.PARSE_ERROR, parseError(invalid), null, List.of(), null);
        }
    }

    private RunResult execute(RunRequest request) {
        ServerPlayerEntity player = requirePlayer(true);
        String command = normalize(request.command());
        DirectorSettings settings = config.director();
        runs.limit(settings.ratePerMinute());
        long now = nowMs();
        if (runs.status(now).remaining() == 0) throw limited(runs, now);

        var captured = new CapturingOutput();
        var result = new CommandResult();
        var source = DirectorSource.create(player, settings, captured).withReturnValueConsumer(result::accept);
        Parsed parsed;
        try {
            parsed = parse(command, source);
        } catch (CommandSyntaxException invalid) {
            return empty(RunStatus.PARSE_ERROR, parseError(invalid), null);
        }
        PolicyDecision policy = policy(settings).evaluate(parsed.shape());
        if (!policy.allowed()) return empty(RunStatus.REJECTED, null, policy);

        if (!log.ready() || !markers.ready()) throw new ApiException(503, "director_storage_unavailable");
        long actionId = log.allocate();
        boolean completed = false;
        var window = ActionWindow.open(actionId, player);
        try {
            CommandManager.callWithContext(source, context ->
                CommandExecutionContext.enqueueCommand(context, command, parsed.chain(), source, ReturnValueConsumer.EMPTY));
            completed = true;
        } catch (RuntimeException failure) {
            LOG.warn("导演指令 #{} 执行失败", actionId, failure);
            source.sendError(Text.translatable("command.failed"));
        } finally {
            window.close();
        }
        var blocks = window.blocks().changes();
        var inventory = window.inventory().changes(player);
        var spawned = window.entities().spawned();
        if (spawned.stream().anyMatch(EntityJournal::counted)) activeEntities = countActive();
        var violations = GuardEvaluator.evaluate(window, blocks, settings, activeEntities);
        boolean rolledBack = !violations.isEmpty() || !completed;
        var irreversible = irreversible(parsed.shape());
        var spawnedSummary = window.entities().summary();
        boolean undoable = !rolledBack && (!blocks.isEmpty() || !inventory.isEmpty() || !spawnedSummary.isEmpty());
        List<Marker> markerSummary = List.of();
        if (rolledBack) {
            undoing = true;
            try { Rollback.run(window, blocks, inventory); }
            finally { undoing = false; }
        } else {
            for (var entity : spawned) {
                if (EntityJournal.fakeDrop(entity)) continue;
                entity.addCommandTag("info2ai");
                entity.addCommandTag("info2ai_a" + actionId);
                if (EntityJournal.counted(entity)) activeEntities++;
            }
            boolean marked = false;
            for (var change : inventory) {
                if (change.before().isEmpty() && !change.after().isEmpty()) {
                    DirectorEvents.mark(player.getInventory().getStack(change.slot()), actionId);
                    marked = true;
                }
            }
            if (marked) inventory = window.inventory().changes(player);
            if (!inventory.isEmpty()) player.currentScreenHandler.sendContentUpdates();
            markerSummary = markers.record(actionId, blocks, spawned).stream().map(marker -> {
                var pos = marker.location().pos();
                return new Marker(marker.block(), List.of(pos.getX(), pos.getY(), pos.getZ()));
            }).toList();
        }
        var changes = new Changes(blocks.size(), spawnedSummary,
            inventory.stream().map(InventoryDiff.Change::summary).toList(), markerSummary);
        undoData.put(actionId, new UndoData(undoable ? blocks : List.of(), undoable ? inventory : List.of(),
            irreversible, undoable));
        while (undoData.size() > settings.undoDepth()) undoData.pollFirstEntry();
        RunStatus status = !violations.isEmpty() ? RunStatus.ROLLED_BACK : completed && result.success ? RunStatus.EXECUTED : RunStatus.FAILED;
        runs.record(status, now);
        log.append(new ActionRecord(actionId, System.currentTimeMillis(), status, request.intent(), command,
            request.visibility(), changes, irreversible, undoable).summary());
        String statusText = status.name().toLowerCase(java.util.Locale.ROOT);
        DirectorEvents.emit(player.getServerWorld(), "director_action", "info",
            "伙伴执行了指令 #" + actionId + "：" + request.intent() + "（" + statusText + "）",
            "action_id", actionId, "status", statusText, "command", command, "blocks", changes.blocks(),
            "spawned", changes.spawned().size(), "vetoes", window.vetoes().size());
        var store = Info2AiApi.store();
        return new RunResult(status, actionId, result.success, result.total, result.calls, captured.output(),
            captured.errors(), null, policy, new GuardResult(rolledBack, violations, window.vetoes()),
            changes, irreversible, undoable, runs.status(nowMs()),
            store == null ? null : store.sequence());
    }

    private int countActive() {
        int count = 0;
        for (var world : server.getWorlds()) count += GuardEvaluator.activeCount(world.iterateEntities());
        return count;
    }

    private CommandPolicy policy(DirectorSettings settings) {
        if (settings != policySettings) {
            serverPolicy = new CommandPolicy(settings);
            policySettings = settings;
        }
        return serverPolicy;
    }

    private static List<String> irreversible(CommandShape shape) {
        var items = new java.util.LinkedHashSet<String>();
        for (var segment : shape.segments()) {
            if (!segment.rootCommand() || segment.path().isEmpty()) continue;
            String root = segment.path().getFirst();
            switch (root) {
                case "effect" -> items.add("effects");
                case "fillbiome" -> items.add("biomes");
                case "weather", "time", "scoreboard", "bossbar" -> items.add(root);
                case "item", "loot", "data" -> {
                    if (segment.path().contains("block") && !segment.path().contains("get")) items.add("container_contents");
                }
                default -> { }
            }
        }
        return List.copyOf(items);
    }

    private Parsed parse(String command, ServerCommandSource source) throws CommandSyntaxException {
        var dispatcher = server.getCommandManager().getDispatcher();
        var parse = dispatcher.parse(command, source);
        CommandManager.throwException(parse);
        var chain = ContextChain.tryFlatten(parse.getContext().build(command)).orElseThrow(() ->
            CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader()));
        return new Parsed(chain, ShapeExtractor.extract(parse, dispatcher.getRoot()));
    }

    private RunResult empty(RunStatus status, ParseError error, PolicyDecision policy) {
        return new RunResult(status, null, false, 0, 0, List.of(), List.of(), error, policy,
            GuardResult.EMPTY, Changes.EMPTY, List.of(), false, runs.status(nowMs()), null);
    }

    private static ParseError parseError(CommandSyntaxException invalid) {
        String context = invalid.getContext();
        return new ParseError(invalid.getRawMessage().getString(), invalid.getCursor(),
            context == null ? null : context.replace("<--[HERE]", "<--[此处]"));
    }

    private void checkContext(boolean requireEnabled) {
        if (requireEnabled && !missingGuards.isEmpty()) throw new ApiException(403, "guard_missing");
        if (requireEnabled && !config.director().enabled()) throw new ApiException(403, "director_disabled");
        if (!running || !playerPresent) throw new ApiException(503, "not_in_world");
        if (Info2AiApi.client().gamePaused()) throw new ApiException(409, "game_paused");
        if (playerDead) throw new ApiException(409, "player_dead");
    }

    private ServerPlayerEntity requirePlayer(boolean requireEnabled) {
        checkContext(requireEnabled);
        if (!server.isOnThread()) throw new ApiException(503, "not_in_world");
        var player = host();
        if (player == null) throw new ApiException(503, "not_in_world");
        if (player.isDead() || player.isRemoved()) throw new ApiException(409, "player_dead");
        return player;
    }

    private ServerPlayerEntity host() {
        var profile = server.getHostProfile();
        return profile == null ? null : server.getPlayerManager().getPlayer(profile.getId());
    }

    private static String normalize(String command) {
        if (command == null || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            throw new ApiException(400, "bad_command");
        }
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank() || command.codePointCount(0, command.length()) > 2000) {
            throw new ApiException(400, "bad_command");
        }
        return command;
    }

    private void acquireRun(long timeout) {
        try {
            if (!inFlight.tryAcquire(timeout, TimeUnit.MILLISECONDS)) throw new ApiException(429, "busy");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "interrupted");
        }
    }

    private static <T> T await(CompletableFuture<T> submitted, long timeout) throws TimeoutException {
        try {
            return submitted.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "interrupted");
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof ApiException api) throw api;
            throw new IllegalStateException("导演服务端任务失败", failure.getCause());
        }
    }

    private static ApiException limited(RateLimiter limiter, long now) {
        return new ApiException(429, "rate_limited", limiter.status(now).resetInSeconds());
    }

    private static long nowMs() { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()); }

    @Override
    public UndoResult undo(UndoRequest request) {
        checkContext(true);
        try {
            return await(server.submit(() -> undoOnServer(request)), config.director().commandTimeoutMs());
        } catch (TimeoutException waiting) {
            throw new ApiException(504, "timeout");
        }
    }

    private UndoResult undoOnServer(UndoRequest request) {
        var player = requirePlayer(true);
        Long id = request.last() ? undoData.entrySet().stream().filter(e -> e.getValue().undoable())
            .map(Map.Entry::getKey).reduce((a, b) -> b).orElse(null) : request.actionId();
        var data = undoData.get(id);
        if (!log.ready() || !markers.ready()) throw new ApiException(503, "director_storage_unavailable");
        if (data == null || !data.undoable()) {
            UndoStatus status = id != null && id < log.nextId() && data == null ? UndoStatus.EXPIRED : UndoStatus.NOT_FOUND;
            int removed = status == UndoStatus.EXPIRED ? removeEntities(id) : 0;
            if (removed > 0) {
                markers.removeAction(id);
                return undoResult(player, UndoStatus.PARTIAL, id, 0, 0, removed, List.of(), List.of(), List.of("expired_changes"));
            }
            return new UndoResult(status, id, 0, 0, 0, List.of(), List.of(), List.of(), null);
        }
        int restored = 0;
        int skipped = 0;
        for (var change : data.blocks().reversed()) {
            var location = change.location();
            if (location.world().getChunkManager().getWorldChunk(location.pos().getX() >> 4, location.pos().getZ() >> 4) != null
                && BlockJournal.stillMatches(change) && Rollback.restoreBlock(change)) restored++;
            else skipped++;
        }
        int removed = removeEntities(id);
        var returned = new java.util.ArrayList<String>();
        var reclaimed = new java.util.ArrayList<String>();
        for (var change : data.inventory()) {
            var before = change.before();
            var after = change.after();
            var current = player.getInventory().getStack(change.slot());
            if (net.minecraft.item.ItemStack.areEqual(current, after)) {
                player.getInventory().setStack(change.slot(), before);
                if (!before.isEmpty() && before.getCount() > after.getCount()) {
                    returned.add(InventoryDiff.describe(before.copyWithCount(before.getCount() - after.getCount())));
                }
                if (!after.isEmpty() && after.getCount() > before.getCount()) {
                    reclaimed.add(InventoryDiff.describe(after.copyWithCount(after.getCount() - before.getCount())));
                }
            } else {
                skippedInventoryReturn(player, before, after, returned);
            }
        }
        if (!data.inventory().isEmpty()) player.currentScreenHandler.sendContentUpdates();
        undoData.put(id, new UndoData(List.of(), List.of(), data.notReverted(), false));
        markers.removeAction(id);
        log.undone(id);
        return undoResult(player, skipped > 0 || !data.notReverted().isEmpty() ? UndoStatus.PARTIAL : UndoStatus.UNDONE,
            id, restored, skipped, removed, returned, reclaimed, data.notReverted());
    }

    private UndoResult undoResult(ServerPlayerEntity player, UndoStatus status, long id, int restored, int skipped,
                                  int removed, List<String> returned, List<String> reclaimed, List<String> notReverted) {
        DirectorEvents.emit(player.getServerWorld(), "director_undone", "info", "撤销了伙伴指令 #" + id,
            "action_id", id, "restored", restored, "skipped", skipped);
        var store = Info2AiApi.store();
        return new UndoResult(status, id, restored, skipped, removed, returned, reclaimed, notReverted,
            store == null ? null : store.sequence());
    }

    private static void skippedInventoryReturn(ServerPlayerEntity player, net.minecraft.item.ItemStack before,
                                               net.minecraft.item.ItemStack after, List<String> returned) {
        if (before.isEmpty()) return;
        int amount = net.minecraft.item.ItemStack.areItemsAndComponentsEqual(before, after)
            ? before.getCount() - after.getCount() : before.getCount();
        if (amount <= 0) return;
        var stack = before.copyWithCount(amount);
        returned.add(InventoryDiff.describe(stack));
        if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) player.dropItem(stack, false);
    }

    private int removeEntities(long id) {
        String tag = "info2ai_a" + id;
        int removed = 0;
        for (var world : server.getWorlds()) {
            var matches = new java.util.ArrayList<net.minecraft.entity.Entity>();
            for (var entity : world.iterateEntities()) {
                if (entity.isAlive() && entity.getCommandTags().contains(tag)) matches.add(entity);
            }
            undoing = true;
            try {
                for (var entity : matches) { entity.discard(); removed++; }
            } finally { undoing = false; }
        }
        activeEntities = countActive();
        return removed;
    }

    private record UndoData(List<BlockJournal.Change> blocks, List<InventoryDiff.Change> inventory,
                            List<String> notReverted, boolean undoable) { }
    private void rebuildCatalogs() {
        try {
            if (commandNotes == null) commandNotes = CommandNotes.load();
            var source = server.getCommandSource().withLevel(config.director().permissionLevel());
            var commands = CommandCatalog.build(server.getCommandManager().getDispatcher(), source, commandNotes);
            catalogs = new Catalogs(commands, RegistryCatalog.build(server));
        } catch (RuntimeException failure) {
            LOG.error("构建导演指令和注册表目录失败", failure);
        }
    }

    @Override public HelpResult help(String commandPath, String topic) {
        var result = requireCatalogs().commands().help(commandPath, topic, new CommandPolicy(config.director()));
        if (result == null) throw new ApiException(404, topic == null ? "unknown_command" : "unknown_topic");
        return result;
    }

    @Override public LookupResult lookup(String kind, String query, int limit) {
        var result = requireCatalogs().registries().lookup(kind, query, limit, config.director());
        if (result == null) throw new ApiException(400, "bad_kind");
        return result;
    }

    private Catalogs requireCatalogs() {
        Catalogs value = catalogs;
        if (value == null) throw new ApiException(503, "director_catalog_unavailable");
        return value;
    }

    @Override
    public com.google.gson.JsonObject selftest() {
        checkContext(false);
        try {
            return await(server.submit(this::selftestOnServer), config.director().commandTimeoutMs());
        } catch (TimeoutException waiting) {
            throw new ApiException(504, "timeout");
        }
    }

    private com.google.gson.JsonObject selftestOnServer() {
        var player = requirePlayer(false);
        var settings = config.director();
        var source = DirectorSource.create(player, settings, new CapturingOutput());
        var policy = policy(settings);
        var examples = requireCatalogs().commands().notes().examples();
        var failures = new com.google.gson.JsonArray();
        for (var example : examples) {
            try {
                var decision = policy.evaluate(parse(normalize(example.command()), source).shape());
                if (!decision.allowed()) failures.add(exampleFailure(example, "policy", decision.reason()));
            } catch (CommandSyntaxException invalid) {
                failures.add(exampleFailure(example, "parse", invalid.getMessage()));
            }
        }
        var result = new com.google.gson.JsonObject();
        result.addProperty("checked", examples.size());
        result.add("failures", failures);
        return result;
    }

    private static com.google.gson.JsonObject exampleFailure(CommandNotes.Example example, String stage, String message) {
        var row = new com.google.gson.JsonObject();
        row.addProperty("file", example.file());
        row.addProperty("line", example.line());
        row.addProperty("command", example.command());
        row.addProperty("stage", stage);
        row.addProperty("message", message);
        return row;
    }

    private record Catalogs(CommandCatalog commands, RegistryCatalog registries) { }
    @Override public List<ActionSummary> actions(int limit) { return log.actions(limit); }
    @Override public StatusResult status() {
        var settings = config.director();
        return new StatusResult(settings.enabled() && missingGuards.isEmpty(), available(), runs.status(nowMs()),
            new Limits(settings.ratePerMinute(), settings.maxBlocksPerCommand(), settings.maxSummonPerCommand(),
                settings.maxActiveEntities()), activeEntities, markers.count(), PolicyRules.DENIED_ROOTS.keySet().stream().sorted().toList(),
            missingGuards);
    }
    @Override public void enabled(boolean on) {
        if (on && !missingGuards.isEmpty()) throw new ApiException(403, "guard_missing");
        try {
            config.directorEnabled(on);
        } catch (java.io.IOException failure) {
            LOG.error("无法保存导演开关", failure);
            throw new ApiException(500, "director_config_write_failed");
        }
    }

    public com.example.info2ai.model.Facts snapshot(ServerPlayerEntity player) {
        return markers.ready() ? DirectorSection.capture(this, player, markers) : null;
    }

    @Override public com.example.info2ai.capture.merge.PerfStats.Sample executionPerf() { return executionPerf.snapshot(); }

    void forgetEntity(net.minecraft.entity.Entity entity) {
        if (server.isOnThread()) {
            markers.removeEntity(entity.getUuid());
            activeEntities = Math.max(0, activeEntities - 1);
        }
    }

    boolean cooldown(String kind, long id, int ticks) {
        String key = kind + ":" + id;
        int now = server.getTicks();
        Integer previous = eventTicks.get(key);
        if (previous != null && now - previous < ticks) return false;
        eventTicks.put(key, now);
        if (eventTicks.size() > 512) eventTicks.pollFirstEntry();
        return true;
    }

    void opened(net.minecraft.server.world.ServerWorld world, net.minecraft.util.math.BlockPos pos) {
        var marker = markers.opened(world, pos);
        if (marker == null) return;
        DirectorEvents.emit(world, "director_container_opened", "info", "玩家打开了伙伴放置的" + marker.block(),
            "action_id", marker.actionId(), "pos", List.of(pos.getX(), pos.getY(), pos.getZ()));
    }

    Long changedAt(net.minecraft.server.world.ServerWorld world, net.minecraft.util.math.BlockPos pos,
                   net.minecraft.block.BlockState state) {
        for (var entry : undoData.reversed().entrySet()) {
            for (var change : entry.getValue().blocks()) {
                if (change.location().world() == world && change.location().pos().equals(pos)
                    && change.after().block().equals(state)) return entry.getKey();
            }
        }
        return null;
    }


    private record Parsed(ContextChain<ServerCommandSource> chain, CommandShape shape) { }

    private static final class CommandResult {
        private boolean success;
        private int total;
        private int calls;

        private void accept(boolean successful, int value) {
            success |= successful;
            total += value;
            calls++;
        }
    }
}

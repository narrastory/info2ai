package com.example.info2ai.director.log;

import com.example.info2ai.api.DirectorRequests.ActionSummary;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ActionLog implements AutoCloseable {
    private final Path directory;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task ->
        Thread.ofPlatform().daemon().name("info2ai-director-writer").unstarted(task));
    private final CompletableFuture<Long> startingId;
    private volatile List<ActionSummary> recent = List.of();
    private volatile boolean failed;
    private long nextId;

    public ActionLog(Path directory) {
        this.directory = directory;
        startingId = CompletableFuture.supplyAsync(() -> {
            Path path = directory.resolve("state.json");
            try {
                Files.createDirectories(directory);
                if (Files.notExists(path)) return 1L;
                long id = JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                    .get("nextActionId").getAsBigDecimal().longValueExact();
                if (id < 1) throw new IllegalArgumentException("无效的动作编号");
                return id;
            } catch (IOException | RuntimeException failure) {
                failed = true;
                LoggerFactory.getLogger("info2ai.director").error("无法读取导演动作编号", failure);
                throw new IllegalStateException(failure);
            }
        }, writer);
    }

    public boolean ready() { return startingId.isDone() && !failed; }

    public long nextId() { return nextId == 0 ? startingId.join() : nextId; }

    public long allocate() {
        long id = nextId();
        nextId = Math.incrementExact(id);
        var data = new JsonObject();
        data.addProperty("nextActionId", nextId);
        write("state.json", data);
        return id;
    }

    public void append(ActionSummary summary) {
        var updated = new ArrayList<ActionSummary>();
        updated.add(summary);
        updated.addAll(recent.subList(0, Math.min(recent.size(), 99)));
        recent = List.copyOf(updated);
        var json = summary.toJson();
        schedule(() -> Files.writeString(directory.resolve("actions.jsonl"), json + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND));
    }

    public void undone(long id) {
        recent = recent.stream().map(a -> a.actionId() == id
            ? new ActionSummary(a.actionId(), a.t(), a.status(), a.intent(), a.command(), a.visibility(),
                a.blocks(), a.spawned(), false, true) : a).toList();
    }

    public List<ActionSummary> actions(int limit) {
        var snapshot = recent;
        return snapshot.subList(0, Math.min(limit, snapshot.size()));
    }

    public void write(String file, JsonObject data) {
        String json = data + "\n";
        schedule(() -> {
            var temp = Files.createTempFile(directory, "director-", ".tmp");
            try {
                Files.writeString(temp, json);
                try {
                    Files.move(temp, directory.resolve(file), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temp, directory.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        });
    }

    public CompletableFuture<JsonObject> read(String file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path path = directory.resolve(file);
                return Files.notExists(path) ? new JsonObject() : JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }, writer);
    }

    private void schedule(Write action) {
        writer.execute(() -> {
            try {
                action.run();
            } catch (IOException | RuntimeException failure) {
                failed = true;
                LoggerFactory.getLogger("info2ai.director").error("写入导演记录失败", failure);
            }
        });
    }

    @FunctionalInterface private interface Write { void run() throws IOException; }

    @Override public void close() { writer.close(); }
}

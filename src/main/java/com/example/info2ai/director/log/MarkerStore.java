package com.example.info2ai.director.log;

import com.example.info2ai.director.guard.BlockJournal;
import com.example.info2ai.director.guard.EntityJournal;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MarkerStore {
    private static final int LIMIT = 256;
    private final ActionLog log;
    private final CompletableFuture<JsonObject> loaded;
    private final LinkedHashMap<Location, Marker> markers = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, EntityMark> entities = new LinkedHashMap<>();
    private boolean initialized;
    private volatile int count;

    public record Location(String dimension, BlockPos pos) { }
    public record Marker(long actionId, Location location, String block, String kind, int openedCount) { }
    public record EntityMark(long actionId, UUID id, String dimension, long createdAt) { }

    public MarkerStore(ActionLog log) {
        this.log = log;
        loaded = log.read("markers.json");
    }

    public boolean ready() {
        if (!loaded.isDone()) return false;
        if (!initialized) {
            var json = loaded.join();
            if (json.has("blocks")) for (var value : json.getAsJsonArray("blocks")) {
                var row = value.getAsJsonObject();
                var location = new Location(row.get("dimension").getAsString(), BlockPos.fromLong(row.get("pos").getAsLong()));
                markers.put(location, new Marker(row.get("action_id").getAsLong(), location, row.get("block").getAsString(),
                    row.get("kind").getAsString(), row.get("opened").getAsInt()));
            }
            if (json.has("entities")) for (var value : json.getAsJsonArray("entities")) {
                var row = value.getAsJsonObject();
                UUID id = UUID.fromString(row.get("uuid").getAsString());
                entities.put(id, new EntityMark(row.get("action_id").getAsLong(), id, row.get("dimension").getAsString(),
                    row.get("created_at").getAsLong()));
            }
            count = markers.size();
            initialized = true;
        }
        return true;
    }

    public List<Marker> record(long id, List<BlockJournal.Change> changes, List<Entity> spawned) {
        var added = new ArrayList<Marker>();
        boolean changed = false;
        for (var change : changes) {
            var world = change.location().world();
            var pos = change.location().pos();
            if (change.before().block().equals(change.after().block())) continue;
            var location = location(world, pos);
            changed |= markers.remove(location) != null;
            if (!change.after().block().hasBlockEntity()) continue;
            var blockEntity = world.getBlockEntity(pos);
            if (blockEntity == null) continue;
            var marker = new Marker(id, location, Registries.BLOCK.getId(change.after().block().getBlock()).toString(),
                blockEntity instanceof Inventory ? "container" : "block_entity", 0);
            markers.put(location, marker);
            added.add(marker);
            changed = true;
        }
        for (var entity : spawned) {
            if (!EntityJournal.counted(entity) || !(entity.getWorld() instanceof ServerWorld world)) continue;
            var idValue = entity.getUuid();
            entities.put(idValue, new EntityMark(id, idValue, world.getRegistryKey().getValue().toString(), System.currentTimeMillis()));
            changed = true;
        }
        if (changed) save();
        return List.copyOf(added);
    }

    public Marker opened(ServerWorld world, BlockPos pos) {
        if (!ready()) return null;
        var key = location(world, pos);
        var marker = markers.get(key);
        if (marker == null || marker.openedCount() != 0 || !marker.kind().equals("container")) return null;
        if (!Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString().equals(marker.block())) return null;
        markers.put(key, new Marker(marker.actionId(), key, marker.block(), marker.kind(), 1));
        save();
        return marker;
    }

    public void removeAction(long id) {
        boolean changed = markers.values().removeIf(marker -> marker.actionId() == id);
        changed |= entities.values().removeIf(mark -> mark.actionId() == id);
        if (changed) save();
    }

    public void removeEntity(UUID id) {
        if (ready() && entities.remove(id) != null) save();
    }

    public int count() { return count; }
    public List<Marker> blocks() { return List.copyOf(markers.values()); }
    public List<EntityMark> entities() { return List.copyOf(entities.values()); }

    private static Location location(ServerWorld world, BlockPos pos) {
        return new Location(world.getRegistryKey().getValue().toString(), pos.toImmutable());
    }

    private void save() {
        while (markers.size() > LIMIT) markers.pollFirstEntry();
        while (entities.size() > LIMIT) entities.pollFirstEntry();
        count = markers.size();
        var root = new JsonObject();
        var blocks = new JsonArray();
        for (var marker : markers.values()) {
            var row = new JsonObject();
            row.addProperty("action_id", marker.actionId());
            row.addProperty("dimension", marker.location().dimension());
            row.addProperty("pos", marker.location().pos().asLong());
            row.addProperty("block", marker.block());
            row.addProperty("kind", marker.kind());
            row.addProperty("opened", marker.openedCount());
            blocks.add(row);
        }
        var spawned = new JsonArray();
        for (var mark : entities.values()) {
            var row = new JsonObject();
            row.addProperty("action_id", mark.actionId());
            row.addProperty("uuid", mark.id().toString());
            row.addProperty("dimension", mark.dimension());
            row.addProperty("created_at", mark.createdAt());
            spawned.add(row);
        }
        root.add("blocks", blocks);
        root.add("entities", spawned);
        log.write("markers.json", root);
    }
}

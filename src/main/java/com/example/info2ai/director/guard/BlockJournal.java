package com.example.info2ai.director.guard;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Clearable;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockJournal {
    private static final int LIMIT = 70_000;
    private final Map<Location, State> before = new LinkedHashMap<>();
    private boolean overflow;

    public record Location(ServerWorld world, BlockPos pos) { }
    public record State(BlockState block, NbtCompound nbt) {
        public State {
            if (nbt != null) nbt = nbt.copy();
        }
        @Override public NbtCompound nbt() { return nbt == null ? null : nbt.copy(); }
    }
    public record Change(Location location, State before, State after) { }

    public boolean record(ServerWorld world, BlockPos pos) {
        var location = new Location(world, pos.toImmutable());
        if (before.containsKey(location)) return true;
        if (before.size() >= LIMIT) {
            overflow = true;
            return false;
        }
        before.put(location, read(location));
        return true;
    }

    public List<Change> changes() {
        return before.entrySet().stream().map(entry -> new Change(entry.getKey(), entry.getValue(), read(entry.getKey())))
            .filter(change -> !change.before().equals(change.after())).toList();
    }

    public static State read(Location location) {
        var entity = location.world().getBlockEntity(location.pos());
        return new State(location.world().getBlockState(location.pos()), entity == null ? null
            : entity.createNbtWithIdentifyingData(location.world().getRegistryManager()));
    }

    public static boolean stillMatches(Change change) {
        State live = read(change.location());
        State placed = change.after();
        if (live.equals(placed)) return true;
        // 树叶的 distance 在下一 tick 自行更新，保留其余状态的比较。
        var distance = net.minecraft.state.property.Properties.DISTANCE_1_7;
        return live.block().isIn(net.minecraft.registry.tag.BlockTags.LEAVES) && live.block().contains(distance)
            && placed.block().contains(distance) && live.nbt() == null && placed.nbt() == null
            && live.block().with(distance, placed.block().get(distance)).equals(placed.block());
    }

    public boolean overflow() { return overflow; }

    public static void clear(Object object) {
        var window = ActionWindow.active();
        if (window != null && object instanceof BlockEntity entity && entity.getWorld() instanceof ServerWorld world
            && !window.blocks().record(world, entity.getPos())) return;
        Clearable.clear(object);
    }
}

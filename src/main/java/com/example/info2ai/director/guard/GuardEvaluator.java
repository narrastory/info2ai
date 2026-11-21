package com.example.info2ai.director.guard;

import com.example.info2ai.api.DirectorRequests.GuardViolation;
import com.example.info2ai.director.DirectorSettings;
import com.example.info2ai.director.policy.CommandShape.BlockPosition;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class GuardEvaluator {
    private GuardEvaluator() { }

    public static List<GuardViolation> evaluate(ActionWindow window, List<BlockJournal.Change> blocks,
                                                DirectorSettings settings, int activeCount) {
        List<GuardViolation> violations = new ArrayList<>();
        var entities = window.entities();
        if (entities.moved().entrySet().stream().anyMatch(e -> e.getKey().getPos().squaredDistanceTo(e.getValue().pos()) > 0.0001)) {
            violations.add(violation("G-MOVE", "不能移动或传送已存在的实体"));
        }
        if (entities.pushed().entrySet().stream().anyMatch(e -> e.getKey().getVelocity().squaredDistanceTo(e.getValue()) > 0.000001)) {
            violations.add(violation("G-PUSH", "不能推动已存在的实体"));
        }
        if (window.blocks().overflow()) violations.add(violation("G-OVERFLOW", "改动规模过大"));
        if (blocks.size() > settings.maxBlocksPerCommand()) {
            violations.add(violation("G-BLOCKS", "单条最多改动 " + settings.maxBlocksPerCommand() + " 个方块"));
        }
        long spawned = entities.spawned().stream().filter(EntityJournal::counted).count();
        if (spawned > settings.maxSummonPerCommand()) {
            violations.add(violation("G-SUMMON", "单条最多生成 " + settings.maxSummonPerCommand() + " 个实体"));
        }
        if (spawned > 0 && activeCount + spawned > settings.maxActiveEntities()) {
            violations.add(violation("G-CAP", "伙伴生成的实体已达上限 " + settings.maxActiveEntities() + "，请先撤销一些"));
        }
        if (entities.spawned().stream().map(EntityJournal::type).anyMatch(settings.summonDenylist()::contains)
            || blocks.stream().anyMatch(change -> forbiddenSpawner(change, settings))) {
            violations.add(violation("G-DENY", "该实体已被禁止生成"));
        }
        if (window.player().getMaxHealth() < window.maxHealth()) {
            violations.add(violation("G-MAXHP", "不能降低玩家的最大生命值"));
        }
        violations.addAll(hazards(window, blocks));
        return List.copyOf(violations);
    }

    private static boolean forbiddenSpawner(BlockJournal.Change change, DirectorSettings settings) {
        var block = change.after().block();
        if (!block.isOf(Blocks.SPAWNER) && !block.isOf(Blocks.TRIAL_SPAWNER)) return false;
        var data = change.after().nbt();
        if (data == null) return false;
        data.remove("id");
        return forbiddenEntityId(data, settings.summonDenylist());
    }

    private static boolean forbiddenEntityId(NbtElement element, List<String> denied) {
        if (element instanceof NbtCompound compound) {
            String id = compound.getString("id");
            if (!id.isEmpty() && denied.contains(id.contains(":") ? id : "minecraft:" + id)) return true;
            return compound.getKeys().stream().anyMatch(key -> forbiddenEntityId(compound.get(key), denied));
        }
        return element instanceof NbtList list && list.stream().anyMatch(value -> forbiddenEntityId(value, denied));
    }

    private static List<GuardViolation> hazards(ActionWindow window, List<BlockJournal.Change> blocks) {
        if (blocks.isEmpty()) return List.of();
        var bounds = window.body();
        var body = new HashSet<BlockPosition>();
        for (BlockPos pos : BlockPos.iterate(MathHelper.floor(bounds.minX), MathHelper.floor(bounds.minY),
            MathHelper.floor(bounds.minZ), MathHelper.ceil(bounds.maxX) - 1, MathHelper.ceil(bounds.maxY) - 1,
            MathHelper.ceil(bounds.maxZ) - 1)) {
            body.add(position(pos));
        }
        var feet = window.feet();
        var inputs = blocks.stream().filter(change -> change.location().world() == window.player().getServerWorld())
            .filter(change -> Math.abs((long) change.location().pos().getX() - feet.getX()) <= 1
                && Math.abs((long) change.location().pos().getZ() - feet.getZ()) <= 1
                && Math.abs((long) change.location().pos().getY() - feet.getY()) <= 3)
            .filter(change -> !change.before().block().equals(change.after().block())).map(change -> {
                var pos = change.location().pos();
                var world = change.location().world();
                var before = change.before().block();
                var after = change.after().block();
                return new HazardRules.BlockChange(position(pos), Registries.BLOCK.getId(before.getBlock()).toString(),
                    Registries.BLOCK.getId(after.getBlock()).toString(), !before.getCollisionShape(world, pos).isEmpty(),
                    !after.getCollisionShape(world, pos).isEmpty(), after.getFluidState().isIn(FluidTags.LAVA));
            }).toList();
        return HazardRules.evaluate(position(feet), body, inputs);
    }

    private static BlockPosition position(BlockPos pos) { return new BlockPosition(pos.getX(), pos.getY(), pos.getZ()); }
    private static GuardViolation violation(String rule, String reason) { return new GuardViolation(rule, reason, null); }

    public static int activeCount(Iterable<Entity> entities) {
        int count = 0;
        for (Entity entity : entities) {
            if (entity.isAlive() && EntityJournal.counted(entity) && entity.getCommandTags().contains("info2ai")) count++;
        }
        return count;
    }
}

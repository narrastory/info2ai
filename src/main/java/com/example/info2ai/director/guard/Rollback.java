package com.example.info2ai.director.guard;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public final class Rollback {
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

    private Rollback() { }

    public static void run(ActionWindow window, List<BlockJournal.Change> blocks, List<InventoryDiff.Change> inventory) {
        for (var change : blocks.reversed()) restoreBlock(change);
        for (Entity entity : window.entities().spawned().reversed()) entity.discard();
        var entities = window.entities();
        entities.moved().forEach((entity, original) -> {
            var pos = original.pos();
            if (entity.isRemoved() || entity.getPos().equals(pos)) return;
            if (entity instanceof ServerPlayerEntity player) {
                player.networkHandler.requestTeleport(pos.x, pos.y, pos.z, original.yaw(), original.pitch());
            } else {
                entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.yaw(), original.pitch());
            }
        });
        entities.pushed().forEach((entity, velocity) -> {
            if (!entity.isRemoved() && !entity.getVelocity().equals(velocity)) {
                entity.setVelocity(velocity);
                entity.velocityModified = true;
            }
        });
        var player = window.player();
        for (var change : inventory) player.getInventory().setStack(change.slot(), change.before());
        if (!inventory.isEmpty()) player.currentScreenHandler.sendContentUpdates();
        if (!new java.util.HashSet<>(player.getStatusEffects()).equals(new java.util.HashSet<>(window.effects()))) {
            player.clearStatusEffects();
            window.effects().forEach(player::addStatusEffect);
        }
    }

    public static boolean restoreBlock(BlockJournal.Change change) {
        var world = change.location().world();
        var pos = change.location().pos();
        var original = change.before();
        if (!world.getBlockState(pos).equals(original.block())
            && !world.setBlockState(pos, original.block(), FLAGS)) return false;
        if (original.nbt() != null) {
            var entity = world.getBlockEntity(pos);
            if (entity == null) return false;
            entity.read(original.nbt(), world.getRegistryManager());
            entity.markDirty();
            world.updateListeners(pos, original.block(), original.block(), Block.NOTIFY_LISTENERS);
        }
        return true;
    }
}

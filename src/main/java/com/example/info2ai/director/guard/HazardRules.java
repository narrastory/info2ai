package com.example.info2ai.director.guard;

import com.example.info2ai.api.DirectorRequests.GuardViolation;
import com.example.info2ai.director.policy.CommandShape.BlockPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HazardRules {
    private static final Set<String> DANGEROUS_BLOCKS = Set.of(
        "minecraft:lava", "minecraft:fire", "minecraft:soul_fire", "minecraft:magma_block", "minecraft:campfire",
        "minecraft:soul_campfire", "minecraft:cactus", "minecraft:wither_rose", "minecraft:sweet_berry_bush",
        "minecraft:pointed_dripstone", "minecraft:powder_snow"
    );

    private HazardRules() { }

    public record BlockChange(BlockPosition pos, String oldBlock, String newBlock, boolean oldCollision,
                              boolean newCollision, boolean newFluidIsLava) { }

    public static List<GuardViolation> evaluate(BlockPosition feet, Set<BlockPosition> bodyBlocks,
                                                List<BlockChange> changes) {
        List<GuardViolation> violations = new ArrayList<>();
        for (BlockChange change : changes) {
            BlockPosition pos = change.pos();
            long dy = (long) pos.y() - feet.y();
            boolean near = Math.abs((long) pos.x() - feet.x()) <= 1 && Math.abs((long) pos.z() - feet.z()) <= 1;
            if (near && dy >= -2 && dy <= 2 && (change.newFluidIsLava() || DANGEROUS_BLOCKS.contains(change.newBlock()))) {
                violations.add(new GuardViolation("G-HAZ-BURN", "不能在玩家身边放置会直接伤人的方块", pos.coordinates()));
            }
            if (near && dy >= -3 && dy <= -1 && change.oldCollision() && !change.newCollision()) {
                violations.add(new GuardViolation("G-HAZ-FLOOR", "不能挖空玩家脚下的支撑", pos.coordinates()));
            }
            if (bodyBlocks.contains(pos) && change.newCollision()) {
                violations.add(new GuardViolation("G-HAZ-SUFFOCATE", "不能把方块放进玩家身体里", pos.coordinates()));
            }
        }
        return List.copyOf(violations);
    }
}

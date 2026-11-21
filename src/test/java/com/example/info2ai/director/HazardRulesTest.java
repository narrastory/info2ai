package com.example.info2ai.director;

import com.example.info2ai.director.guard.HazardRules;
import com.example.info2ai.director.guard.HazardRules.BlockChange;
import com.example.info2ai.director.policy.CommandShape.BlockPosition;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HazardRulesTest {
    private static final BlockPosition FEET = new BlockPosition(10, 64, -4);
    private static final BlockPosition HEAD = new BlockPosition(10, 65, -4);

    @Test
    public void detectsDangerousBlocksAndLavaFluidAtBothVerticalBoundaries() {
        var low = change(11, 62, -5, "minecraft:lava", false, false, false);
        var high = change(9, 66, -3, "minecraft:air", false, false, true);
        assertEquals(List.of("G-HAZ-BURN", "G-HAZ-BURN"), rules(low, high));
        assertEquals(List.of(11, 62, -5), HazardRules.evaluate(FEET, Set.of(FEET, HEAD), List.of(low)).getFirst().pos());
        assertTrue(rules(change(11, 61, -4, "minecraft:fire", false, false, false),
            change(11, 67, -4, "minecraft:powder_snow", false, false, false),
            change(11, 64, -4, "minecraft:water", false, false, false)).isEmpty());
    }

    @Test
    public void allowsTrapsTwoOrMoreBlocksAway() {
        assertTrue(rules(change(12, 63, -4, "minecraft:lava", true, false, true),
            change(16, 63, -4, "minecraft:air", true, false, false)).isEmpty());
    }

    @Test
    public void detectsSupportRemovalOnlyInsideTheSpecifiedRange() {
        assertEquals(List.of("G-HAZ-FLOOR", "G-HAZ-FLOOR"),
            rules(change(10, 63, -4, "minecraft:air", true, false, false),
                change(9, 61, -3, "minecraft:air", true, false, false)));
        assertTrue(rules(change(10, 60, -4, "minecraft:air", true, false, false),
            change(10, 62, -4, "minecraft:air", false, false, false),
            change(10, 63, -4, "minecraft:stone", true, true, false)).isEmpty());
    }

    @Test
    public void detectsCollisionOnlyWhereThePlayersBodyActuallyReaches() {
        assertEquals(List.of("G-HAZ-SUFFOCATE"),
            rules(change(10, 65, -4, "minecraft:stone", false, true, false)));
        assertTrue(rules(change(11, 65, -4, "minecraft:stone", false, true, false),
            change(10, 65, -4, "minecraft:torch", false, false, false)).isEmpty());
    }

    private static List<String> rules(BlockChange... changes) {
        return HazardRules.evaluate(FEET, Set.of(FEET, HEAD), List.of(changes)).stream()
            .map(violation -> violation.rule()).toList();
    }

    private static BlockChange change(int x, int y, int z, String newBlock, boolean oldCollision,
                                      boolean newCollision, boolean lavaFluid) {
        return new BlockChange(new BlockPosition(x, y, z), "minecraft:dirt", newBlock, oldCollision, newCollision, lavaFluid);
    }
}

package com.example.info2ai.director.guard;

import com.example.info2ai.api.DirectorRequests.InventoryChange;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

public final class InventoryDiff {
    public record Change(int slot, ItemStack before, ItemStack after) {
        public Change {
            before = before.copy();
            after = after.copy();
        }
        @Override public ItemStack before() { return before.copy(); }
        @Override public ItemStack after() { return after.copy(); }
        public InventoryChange summary() { return new InventoryChange(slot, describe(before), describe(after)); }
    }

    private final List<ItemStack> before;

    InventoryDiff(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        var stacks = new ArrayList<ItemStack>(inventory.size());
        for (int slot = 0; slot < inventory.size(); slot++) stacks.add(inventory.getStack(slot).copy());
        before = List.copyOf(stacks);
    }

    public List<Change> changes(ServerPlayerEntity player) {
        var changes = new ArrayList<Change>();
        for (int slot = 0; slot < before.size(); slot++) {
            ItemStack after = player.getInventory().getStack(slot);
            if (!ItemStack.areEqual(before.get(slot), after)) changes.add(new Change(slot, before.get(slot), after));
        }
        return List.copyOf(changes);
    }

    public static String describe(ItemStack stack) {
        return stack.isEmpty() ? null : Registries.ITEM.getId(stack.getItem()) + " x" + stack.getCount();
    }
}

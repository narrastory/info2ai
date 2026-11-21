package com.example.info2ai.mixin;

import com.example.info2ai.director.DirectorEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
abstract class DirectorInteractionMixin {
    @Unique private int info2ai$previousScreen;

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void info2ai$before(ServerPlayerEntity player, World world, ItemStack stack, Hand hand,
                               BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        info2ai$previousScreen = player.currentScreenHandler.syncId;
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void info2ai$after(ServerPlayerEntity player, World world, ItemStack stack, Hand hand,
                              BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (info2ai$previousScreen != player.currentScreenHandler.syncId && cir.getReturnValue().isAccepted()) {
            DirectorEvents.opened(player, hit.getBlockPos());
        }
    }
}

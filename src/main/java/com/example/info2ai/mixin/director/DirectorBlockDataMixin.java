package com.example.info2ai.mixin.director;

import com.example.info2ai.director.guard.ActionWindow;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.command.BlockDataObject;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockDataObject.class)
abstract class DirectorBlockDataMixin {
    @Shadow @Final private BlockEntity blockEntity;

    @Inject(method = "setNbt", at = @At("HEAD"), cancellable = true, require = 1)
    private void info2ai$record(NbtCompound nbt, CallbackInfo ci) {
        var window = ActionWindow.active();
        if (window != null && (blockEntity instanceof MobSpawnerBlockEntity || blockEntity instanceof TrialSpawnerBlockEntity)
            && blockEntity.getWorld() instanceof ServerWorld world && !window.blocks().record(world, blockEntity.getPos())) {
            ci.cancel();
        }
    }
}

package com.example.info2ai.mixin.director;

import com.example.info2ai.director.guard.ActionWindow;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
abstract class DirectorWorldMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void info2ai$record(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
        var window = ActionWindow.active();
        if (window != null && (Object) this instanceof ServerWorld world && !window.blocks().record(world, pos)) {
            cir.setReturnValue(false);
        }
    }
}

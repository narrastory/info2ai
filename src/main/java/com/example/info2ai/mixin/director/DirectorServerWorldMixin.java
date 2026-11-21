package com.example.info2ai.mixin.director;

import com.example.info2ai.director.guard.ActionWindow;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
abstract class DirectorServerWorldMixin {
    @Inject(method = "addEntity", at = @At("RETURN"), require = 1)
    private void info2ai$spawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        var window = ActionWindow.active();
        if (window != null && cir.getReturnValueZ()) window.entities().spawn(entity);
    }
}

package com.example.info2ai.mixin.director;

import com.example.info2ai.director.guard.ActionWindow;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class DirectorEntityMixin {
    @Inject(method = "setPos", at = @At("HEAD"), require = 1)
    private void info2ai$move(double x, double y, double z, CallbackInfo ci) {
        var window = ActionWindow.active();
        if (window != null) window.entities().move((Entity) (Object) this);
    }

    @Inject(method = {"setVelocity(Lnet/minecraft/util/math/Vec3d;)V", "addVelocity(Lnet/minecraft/util/math/Vec3d;)V",
        "addVelocityInternal(Lnet/minecraft/util/math/Vec3d;)V"}, at = @At("HEAD"), require = 3)
    private void info2ai$pushVec(Vec3d velocity, CallbackInfo ci) {
        info2ai$push();
    }

    @Inject(method = {"setVelocity(DDD)V", "addVelocity(DDD)V", "setVelocityClient(DDD)V"},
        at = @At("HEAD"), require = 3)
    private void info2ai$pushXYZ(double x, double y, double z, CallbackInfo ci) {
        info2ai$push();
    }

    private void info2ai$push() {
        var window = ActionWindow.active();
        if (window != null) window.entities().push((Entity) (Object) this);
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true, require = 1)
    private void info2ai$remove(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof LivingEntity living && ActionWindow.vetoRemoval(living, reason)) ci.cancel();
    }
}

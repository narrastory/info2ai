package com.example.info2ai.mixin.director;

import com.example.info2ai.director.guard.ActionWindow;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStandEntity.class)
abstract class DirectorArmorStandMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true, require = 1)
    private void info2ai$damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (ActionWindow.veto((ArmorStandEntity) (Object) this, "damage", (double) amount)) cir.setReturnValue(false);
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true, require = 1)
    private void info2ai$kill(CallbackInfo ci) {
        if (ActionWindow.veto((ArmorStandEntity) (Object) this, "kill", null)) ci.cancel();
    }
}

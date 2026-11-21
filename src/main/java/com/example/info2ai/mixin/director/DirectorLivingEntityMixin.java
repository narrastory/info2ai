package com.example.info2ai.mixin.director;

import com.example.info2ai.director.guard.ActionWindow;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class DirectorLivingEntityMixin {
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void info2ai$instant(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        var living = (LivingEntity) (Object) this;
        if (ActionWindow.active() == null || living.getWorld().isClient()) return;
        var type = effect.getEffectType();
        boolean harms = living.hasInvertedHealingAndHarm() ? type.equals(StatusEffects.INSTANT_HEALTH)
            : type.equals(StatusEffects.INSTANT_DAMAGE);
        if (harms && ActionWindow.veto(living, "damage", (double) (6 << effect.getAmplifier()))) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true, require = 1)
    private void info2ai$damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (ActionWindow.veto((LivingEntity) (Object) this, "damage", (double) amount)) cir.setReturnValue(false);
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, require = 1)
    private void info2ai$health(float health, CallbackInfo ci) {
        if (ActionWindow.vetoHealth((LivingEntity) (Object) this, health)) ci.cancel();
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true, require = 1)
    private void info2ai$kill(CallbackInfo ci) {
        if (ActionWindow.veto((LivingEntity) (Object) this, "kill", null)) ci.cancel();
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true, require = 1)
    private void info2ai$remove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (ActionWindow.vetoRemoval((LivingEntity) (Object) this, reason)) ci.cancel();
    }
}

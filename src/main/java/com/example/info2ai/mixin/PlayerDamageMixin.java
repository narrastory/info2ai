package com.example.info2ai.mixin;

import com.example.info2ai.capture.server.ServerCapture;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional observation only; missing/partial injections keep last_damage unknown with diagnostics. */
@Mixin(PlayerEntity.class)
public abstract class PlayerDamageMixin {
    @Inject(method = "applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V", at = @At("HEAD"), require = 0)
    private void info2ai$beforeAppliedDamage(DamageSource source, float amount, CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayerEntity p && p.getServer().isOnThread()) {
            ServerCapture.beginAppliedDamage(p);
        }
    }

    @Inject(method = "applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V", at = @At("RETURN"), require = 0)
    private void info2ai$afterAppliedDamage(DamageSource source, float amount, CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayerEntity p && p.getServer().isOnThread()) {
            ServerCapture.endAppliedDamage(p, source);
        }
    }
}

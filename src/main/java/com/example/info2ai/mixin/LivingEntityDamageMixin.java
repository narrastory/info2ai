package com.example.info2ai.mixin;
import com.example.info2ai.capture.server.*;
import com.example.info2ai.events.Observation.ActionType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 与玩家专用钩子分离，实际扣血/吸收完成后才记录玩家攻击。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
    @Unique private double info2ai$beforeHealth,info2ai$beforeAbsorption;
    @Unique private boolean info2ai$pending;
    @Inject(method="applyDamage",at=@At("HEAD"),require=0)
    private void info2ai$before(DamageSource source,float amount,CallbackInfo ci) {
        LivingEntity self=(LivingEntity)(Object)this;
        if(!self.getWorld().isClient&&source.getAttacker() instanceof ServerPlayerEntity) {
            info2ai$beforeHealth=self.getHealth();
            info2ai$beforeAbsorption=self.getAbsorptionAmount();
            info2ai$pending=true;
        }
    }
    @Inject(method="applyDamage",at=@At("RETURN"),require=0)
    private void info2ai$after(DamageSource source,float amount,CallbackInfo ci) {
        LivingEntity self=(LivingEntity)(Object)this;
        if(info2ai$pending&&source.getAttacker() instanceof ServerPlayerEntity p) {
            info2ai$pending=false;
            ServerCapture.eventFact(p,()-> {
                double actual=Math.max(0,info2ai$beforeHealth-self.getHealth())+Math.max(0,info2ai$beforeAbsorption-self.getAbsorptionAmount());
                return actual<=0?null:ServerFactHooks.action(p,ActionType.ATTACK,Registries.ENTITY_TYPE.getId(self.getType()).toString(),1,actual,
                source.getTypeRegistryEntry().getKey().map(k->k.getValue().toString()).orElse(null),null);
            } );
        }
    }
}

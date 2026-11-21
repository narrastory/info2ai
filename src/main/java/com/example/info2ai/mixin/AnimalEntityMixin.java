package com.example.info2ai.mixin;
import com.example.info2ai.capture.server.*;
import com.example.info2ai.events.Observation.ActionType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** 点击本身不算喂养：核对动物繁殖/成长状态确实改变。 */
@Mixin(AnimalEntity.class)
public abstract class AnimalEntityMixin {
    @Unique private int info2ai$age,info2ai$love;
    @Inject(method="interactMob",at=@At("HEAD"),require=0)
    private void info2ai$before(PlayerEntity player,Hand hand,CallbackInfoReturnable<ActionResult> result) {
        if(player instanceof ServerPlayerEntity p)ServerCapture.eventFact(p,()-> {
            var self=(AnimalEntity)(Object)this;
            info2ai$age=self.getBreedingAge();
            info2ai$love=self.getLoveTicks();
            return null;
        } );
    }
    @Inject(method="interactMob",at=@At("RETURN"),require=0)
    private void info2ai$after(PlayerEntity player,Hand hand,CallbackInfoReturnable<ActionResult> result) {
        if(player instanceof ServerPlayerEntity p)ServerCapture.eventFact(p,()-> {
            var self=(AnimalEntity)(Object)this;
            return self.getBreedingAge()==info2ai$age&&self.getLoveTicks()==info2ai$love?null:ServerFactHooks.action(p,ActionType.ANIMAL,
            Registries.ENTITY_TYPE.getId(self.getType()).toString(),1,0,null,null);
        } );
    }
}

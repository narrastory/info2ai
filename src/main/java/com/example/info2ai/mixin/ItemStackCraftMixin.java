package com.example.info2ai.mixin;
import com.example.info2ai.capture.server.*;
import com.example.info2ai.events.Observation.ActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemStack.class)
public abstract class ItemStackCraftMixin {
    @Inject(method="onCraftByPlayer",at=@At("RETURN"),require=0)
    private void info2ai$crafted(World world,PlayerEntity player,int amount,CallbackInfo ci) {
        if(player instanceof ServerPlayerEntity p&&amount>0)ServerCapture.eventFact(p,()->ServerFactHooks.action(p,ActionType.CRAFT,
        Registries.ITEM.getId(((ItemStack)(Object)this).getItem()).toString(),amount,0,null,null));
    }
}

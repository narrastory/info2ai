package com.example.info2ai.mixin.director;

import com.example.info2ai.director.DirectorEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class DirectorItemEntityMixin {
    @Unique private Long info2ai$action;
    @Unique private int info2ai$count;
    @Unique private String info2ai$item;

    @Inject(method = "onPlayerCollision", at = @At("HEAD"))
    private void info2ai$before(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity)) return;
        var self = (ItemEntity) (Object) this;
        info2ai$action = DirectorEvents.action(self);
        if (info2ai$action == null) return;
        var stack = self.getStack();
        info2ai$count = stack.getCount();
        info2ai$item = Registries.ITEM.getId(stack.getItem()).toString();
    }

    @Inject(method = "onPlayerCollision", at = @At("RETURN"))
    private void info2ai$after(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || info2ai$action == null) return;
        var self = (ItemEntity) (Object) this;
        int picked = self.isRemoved() ? info2ai$count : info2ai$count - self.getStack().getCount();
        DirectorEvents.pickedUp(serverPlayer, info2ai$action, info2ai$item, picked);
        info2ai$action = null;
    }
}

package com.example.info2ai.mixin;

import com.example.info2ai.capture.server.ServerFactHooks;
import net.minecraft.block.BlockState;
import net.minecraft.item.BedItem;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BedItem直接setBlockState、不调用BlockItem的同名方法，必须观察该覆盖。 */
@Mixin(BedItem.class)
public abstract class BedItemMixin {
    @Inject(method="place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z",at=@At("RETURN"),require=0)
    private void info2ai$placed(ItemPlacementContext context,BlockState state,CallbackInfoReturnable<Boolean> result) {
        ServerFactHooks.placed(context,state,result.getReturnValueZ());
    }
}

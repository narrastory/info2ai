package com.example.info2ai.mixin;
import com.example.info2ai.capture.server.ServerFactHooks;
import net.minecraft.item.*;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** 观察真实写入后的布尔结果，完全不改原返回值。 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method="place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z",at=@At("RETURN"),require=0)
    private void info2ai$placed(ItemPlacementContext ctx,BlockState state,CallbackInfoReturnable<Boolean> result) {
        ServerFactHooks.placed(ctx,state,result.getReturnValueZ());
    }
}

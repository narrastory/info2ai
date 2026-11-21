package com.example.info2ai.mixin;
import com.example.info2ai.capture.server.*;
import com.example.info2ai.events.*;
import com.example.info2ai.events.Observation.ActionType;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** 返回 true 不证明附魔成功：比对物品组件及真正增加的附魔统计。 */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
    @Shadow @Final private Inventory inventory;
    @Unique private ItemStack info2ai$before=ItemStack.EMPTY;
    @Unique private int info2ai$stat;
    @Inject(method="onButtonClick",at=@At("HEAD"),require=0)
    private void info2ai$before(PlayerEntity player,int id,CallbackInfoReturnable<Boolean> result) {
        if(player instanceof ServerPlayerEntity p)ServerCapture.eventFact(p,()-> {
            info2ai$before=inventory.getStack(0).copy();
            info2ai$stat=p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.ENCHANT_ITEM));
            return null;
        } );
    }
    @Inject(method="onButtonClick",at=@At("RETURN"),require=0)
    private void info2ai$after(PlayerEntity player,int id,CallbackInfoReturnable<Boolean> result) {
        if(player instanceof ServerPlayerEntity p)ServerCapture.eventFact(p,()-> {
            ItemStack after=inventory.getStack(0);
            if(info2ai$before.isEmpty()||!ActionEvidence.enchanted(result.getReturnValueZ(),!ItemStack.areItemsAndComponentsEqual(info2ai$before,
            after),info2ai$stat,p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.ENCHANT_ITEM))))return null;
            var item=ItemCapture.read(after,null);
            info2ai$before=ItemStack.EMPTY;
            return ServerFactHooks.action(p,ActionType.ENCHANT,item.id(),1,0,null,EventData.of("enchantments",item.enchantments()));
        } );
    }
}

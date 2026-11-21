package com.example.info2ai.mixin;
import com.example.info2ai.capture.server.*;
import com.example.info2ai.events.Observation.ActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerFactsMixin {
    @org.spongepowered.asm.mixin.Unique private ItemStack info2ai$usedStack = ItemStack.EMPTY;

    @Inject(method="consumeItem",at=@At("HEAD"),require=0)
    private void info2ai$beforeConsume(CallbackInfo ci) {
        var p = (ServerPlayerEntity) (Object) this;
        var stack = p.getActiveItem();
        info2ai$usedStack = com.example.info2ai.director.DirectorEvents.action(stack) != null
            && (stack.contains(DataComponentTypes.FOOD) || stack.isOf(net.minecraft.item.Items.POTION))
            ? stack.copy() : ItemStack.EMPTY;
    }

    @Inject(method="consumeItem",at=@At("RETURN"),require=0)
    private void info2ai$afterConsume(CallbackInfo ci) {
        var p = (ServerPlayerEntity) (Object) this;
        if (!info2ai$usedStack.isEmpty() && !p.isUsingItem()) {
            com.example.info2ai.director.DirectorEvents.used(p, info2ai$usedStack);
        }
        info2ai$usedStack = ItemStack.EMPTY;
    }
    @Inject(method="sendEquipmentBreakStatus",at=@At("RETURN"),require=0)
    private void info2ai$tool(Item item,EquipmentSlot slot,CallbackInfo ci) {
        var p=(ServerPlayerEntity)(Object)this;
        if(slot==EquipmentSlot.MAINHAND)ServerCapture.eventFact(p,()->ServerFactHooks.action(p,ActionType.TOOL_BREAK,
        Registries.ITEM.getId(item).toString(),1,0,null,null));
    }
    @Inject(method="unlockRecipes(Ljava/util/Collection;)I",at=@At("RETURN"),require=0)
    private void info2ai$recipes(Collection<RecipeEntry<?>> recipes,CallbackInfoReturnable<Integer> result) {
        var p=(ServerPlayerEntity)(Object)this;
        if(result.getReturnValueI()>0)ServerCapture.eventFact(p,()->ServerFactHooks.action(p,ActionType.RECIPE,"recipe",result.getReturnValueI(),0,null,null));
    }
}

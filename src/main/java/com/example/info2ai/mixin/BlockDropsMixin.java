package com.example.info2ai.mixin;
import com.example.info2ai.capture.server.*;
import com.example.info2ai.events.Observation.ActionType;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;
import java.util.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Block.class)
public abstract class BlockDropsMixin {
    @Unique private final ThreadLocal<Set<UUID>> info2ai$beforeDrops=new ThreadLocal<>();
    @Inject(method="afterBreak",at=@At("HEAD"),require=0)
    private void info2ai$before(World world,PlayerEntity player,BlockPos pos,BlockState state,BlockEntity be,ItemStack tool,CallbackInfo ci) {
        if(player instanceof ServerPlayerEntity p)ServerCapture.eventFact(p,()-> {
            Set<UUID> before=new HashSet<>();
            for(var item:world.getEntitiesByClass(ItemEntity.class,new Box(pos).expand(1),e->true))before.add(item.getUuid());
            info2ai$beforeDrops.set(before);
            return null;
        } );
    }
    @Inject(method="afterBreak",at=@At("RETURN"),require=0)
    private void info2ai$after(World world,PlayerEntity player,BlockPos pos,BlockState state,BlockEntity be,ItemStack tool,CallbackInfo ci) {
        if(player instanceof ServerPlayerEntity p)ServerCapture.eventFact(p,()-> {
            Set<UUID> before=info2ai$beforeDrops.get();
            info2ai$beforeDrops.remove();
            if(before==null||p.getInventory().getEmptySlot()!=-1)return null;
            for(var item:world.getEntitiesByClass(ItemEntity.class,new Box(pos).expand(1),e->!before.contains(e.getUuid())))ServerCapture.eventFact(p,
            ()->new com.example.info2ai.events.Observation.Action(ActionType.DROP,Registries.ITEM.getId(item.getStack().getItem()).toString(),
            item.getStack().getCount(),0,null,p.getUuidAsString(),true,true,false,false,java.util.List.of(item.getX(),item.getY(),item.getZ()),null));
            return null;
        } );
    }
}

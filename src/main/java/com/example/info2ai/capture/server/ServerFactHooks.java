package com.example.info2ai.capture.server;
import com.example.info2ai.events.*;
import com.example.info2ai.events.Observation.*;
import com.example.info2ai.model.Facts;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import java.util.List;
/** 成功回调只提交不可变事实；所有读取在 ServerCapture 的异常边界内执行。 */
public final class ServerFactHooks {
    private ServerFactHooks() {
    }
    public static Action action(ServerPlayerEntity p,ActionType type,String id,int count,double amount,String source,Facts data) {
        return new Action(type,id,count,amount,source,p.getUuidAsString(),true,true,false,false,List.of(p.getX(),p.getY(),p.getZ()),data);
    }
    public static void block(ServerPlayerEntity p,ActionType type,BlockPos pos,BlockState state) {
        ServerCapture.eventFact(p,()->new Action(type,Registries.BLOCK.getId(state.getBlock()).toString(),1,0,null,p.getUuidAsString(),true,true,
        state.isIn(BlockTags.BASE_STONE_OVERWORLD)||state.isIn(BlockTags.BASE_STONE_NETHER)||state.isOf(net.minecraft.block.Blocks.DEEPSLATE)||state.isOf(net.minecraft.block.Blocks.STONE),
        state.isIn(BlockTags.CROPS),List.of((double)pos.getX(),(double)pos.getY(),(double)pos.getZ()),null));
    }
    /** BlockItem与不调用父类的BedItem真实RETURN共用相同的结果核验。 */
    public static void placed(net.minecraft.item.ItemPlacementContext ctx,BlockState state,boolean succeeded) {
        if(!succeeded)return;
        if (ctx.getPlayer() instanceof ServerPlayerEntity p) com.example.info2ai.director.DirectorEvents.used(p, ctx.getStack());
        if(ctx.getPlayer() instanceof ServerPlayerEntity p)ServerCapture.eventFact(p,()-> {
            String expected=Registries.BLOCK.getId(state.getBlock()).toString();
            String actual=Registries.BLOCK.getId(ctx.getWorld().getBlockState(ctx.getBlockPos()).getBlock()).toString();
            if(!ActionEvidence.placed(succeeded,expected,actual))return null;
            return new Action(ActionType.PLACE,expected,1,0,null,p.getUuidAsString(),true,true,false,
                state.isIn(BlockTags.CROPS),List.of((double)ctx.getBlockPos().getX(),
                    (double)ctx.getBlockPos().getY(),(double)ctx.getBlockPos().getZ()),null);
        });
    }

}

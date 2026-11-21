package com.example.info2ai.capture.server;
import com.example.info2ai.events.PresenceScan;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.tag.BlockTags;
/** 只检查已加载区块：附近床分128格/游戏tick扫描，支撑最多检查32格深度。 */
public final class EventEnvironmentProbe {
    private static final int RADIUS=16,SIDE=33;
    private final PresenceScan beds=new PresenceScan(SIDE*SIDE*SIDE);
    private BlockPos anchor;
    private String dimension;
    private long completedAt;
    public void invalidate() {
        anchor=null;
    }
    public Boolean nearbyBed(ServerPlayerEntity p) {
        String dim=p.getServerWorld().getRegistryKey().getValue().toString();
        if(!p.getBlockPos().equals(anchor)||!dim.equals(dimension)||beds.complete()&&p.getServer().getTicks()-completedAt>=20) {
            beds.reset();
            anchor=p.getBlockPos().toImmutable();
            dimension=dim;
        }
        boolean wasComplete=beds.complete();
        beds.step(128,index-> {
            int dx=index%SIDE-RADIUS,dy=index/SIDE%SIDE-RADIUS,dz=index/(SIDE*SIDE)-RADIUS;
            var pos=anchor.add(dx,dy,dz);
            var world=p.getServerWorld();
            if(pos.getY()<world.getBottomY()||pos.getY()>=world.getTopY())return false;
            var chunk=world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);
            return chunk==null?null:chunk.getBlockState(pos).isIn(BlockTags.BEDS);
        } );
        if(!wasComplete&&beds.complete())completedAt=p.getServer().getTicks();
        return beds.result();
    }
    public Double fallHeight(ServerPlayerEntity p) {
        if(p.isOnGround()||p.getAbilities().flying||p.isFallFlying()||p.hasVehicle()||p.isClimbing()||p.isTouchingWater())return null;
        var world=p.getServerWorld();
        var bounds=p.getBoundingBox();
        Double nearest=null;
        for(double x:new double[] {
            bounds.minX+.001,bounds.maxX-.001
        } )for(double z:new double[] {
            bounds.minZ+.001,bounds.maxZ-.001
        } ) {
            Double support=null;
            for(int depth=1;depth<=32;depth++) {
                var pos=BlockPos.ofFloored(x,p.getY()-.001,z).down(depth-1);
                if(pos.getY()<world.getBottomY())return null;
                var chunk=world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);
                if(chunk==null)return null;
                var shape=chunk.getBlockState(pos).getCollisionShape(world,pos);
                if(!shape.isEmpty()) {
                    support=p.getY()-(pos.getY()+shape.getMax(net.minecraft.util.math.Direction.Axis.Y));
                    break;
                }
            }
            if(support==null)return null;
            nearest=nearest==null?support:Math.min(nearest,support);
        }
        return nearest;
    }
}

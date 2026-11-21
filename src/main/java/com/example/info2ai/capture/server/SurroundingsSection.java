package com.example.info2ai.capture.server;
import com.example.info2ai.model.*;
import com.example.info2ai.util.VecUtil;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;
import net.minecraft.world.LightType;
import net.minecraft.world.Heightmap;
import java.util.*;
import static com.example.info2ai.capture.server.CaptureJson.*;
public final class SurroundingsSection {
    private java.util.List<com.example.info2ai.events.Observation.Mob> eventMobs=java.util.List.of();
    private long eventMobsMicros;
    public long eventMobsMicros() {return eventMobsMicros;}
    public java.util.List<com.example.info2ai.events.Observation.Mob> eventMobs() {return eventMobs;}
    private final EnclosureSampler enclosure=new EnclosureSampler();
    public SurroundingsInfo collect(ServerPlayerEntity p) {
        var world=p.getServerWorld();var pos=p.getBlockPos();String dimension=world.getRegistryKey().getValue().toString();
        var biome=world.getBiome(pos);String biomeId=biome.getKey().map(k->k.getValue().toString()).orElse(null);
        Double ratio=enclosure.sample(dimension,pos.getX()>>4,pos.getZ()>>4,i->{
            var target=pos.offset(Direction.values()[i/8],i%8+1);
            if(world.getChunkManager().getWorldChunk(target.getX()>>4,target.getZ()>>4)==null)return -1;
            return isEnclosing(world.getBlockState(target)) ? 1 : 0;
        });
        var entities=world.getEntitiesByClass(Entity.class,new Box(p.getPos(),p.getPos()).expand(32),e->e!=p);
        long eventStart=System.nanoTime();
        eventMobs=entities.stream().map(e->new com.example.info2ai.events.Observation.Mob(e.getUuidAsString(),Registries.ENTITY_TYPE.getId(e.getType()).toString(),(double)p.distanceTo(e),e instanceof HostileEntity,e instanceof MobEntity mob&&mob.getTarget()==p)).toList();
        eventMobsMicros=(System.nanoTime()-eventStart)/1000;
        Map<String,Integer> counts=new LinkedHashMap<>();for(String key:List.of("hostile","passive","neutral","projectile","item","other"))counts.put(key,0);
        for(var e:entities)counts.merge(category(e),1,Integer::sum);
        List<EntitySummary> summaries=EntityCandidates.retain(entities,p::squaredDistanceTo).stream().map(e->describe(p,e)).toList();
        int block=world.getLightLevel(LightType.BLOCK,pos),sky=world.getLightLevel(LightType.SKY,pos);
        return new SurroundingsInfo(biomeId,biomeId,facts("block",block,"sky",sky,"total",Math.max(block,sky)),world.isSkyVisible(pos),
            (double)biome.value().getTemperature(),null,ratio,world.getTopY(Heightmap.Type.MOTION_BLOCKING,pos.getX(),pos.getZ())-pos.getY(),
            null,summaries,Facts.fromJson(object(counts.entrySet().stream().flatMap(e->java.util.stream.Stream.of(e.getKey(),e.getValue())).toArray())),null,null,null,null,null);
    }
    // The brief counts non-air, non-liquid blocks; fluid-state emptiness would change waterlogged-solid semantics.
    @SuppressWarnings("deprecation")
    private static boolean isEnclosing(net.minecraft.block.BlockState state) {
        return !state.isAir() && !state.isLiquid();
    }

    private static String category(Entity e) {
        if(e instanceof ItemEntity)return "item";
        if(e instanceof ProjectileEntity)return "projectile";
        if(e instanceof HostileEntity)return "hostile";
        if(e instanceof Angerable)return "neutral";
        if(e instanceof PassiveEntity || e instanceof WaterCreatureEntity || e instanceof AmbientEntity)return "passive";
        return "other";
    }
    private static EntitySummary describe(ServerPlayerEntity p,Entity e) {
        LivingEntity living=e instanceof LivingEntity le?le:null;
        return new EntitySummary(Registries.ENTITY_TYPE.getId(e.getType()).toString(),e.hasCustomName()?e.getCustomName().getString():null,e.getId(),vector(e.getPos()),
            (double)p.distanceTo(e),VecUtil.directionFromDelta(e.getX()-p.getX(),e.getZ()-p.getZ()),living==null?null:(double)living.getHealth(),
            e instanceof HostileEntity,e instanceof MobEntity mob && mob.getTarget()==p,e.isGlowing(),living!=null && living.isBaby(),
            e instanceof TameableEntity t?t.isTamed():null,living==null?null:ItemCapture.read(living.getMainHandStack(),null));
    }
}

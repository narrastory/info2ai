package com.example.info2ai.capture.server;
import com.example.info2ai.model.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;
import java.util.*;
import static com.example.info2ai.capture.server.CaptureJson.*;
/** Only existing loaded WorldChunks are visited. No coordinate block scan or loot generation. */
public final class BlockEntitySection {
    private record Cached(BlockPos pos,Facts facts) { }
    private final Map<BlockPos,Cached> cache=new HashMap<>();
    private List<BlockPos> candidates=List.of();
    private int frame;
    private String dimension;
    public List<Facts> collect(ServerPlayerEntity p) {
        var world=p.getServerWorld();String dim=world.getRegistryKey().getValue().toString();
        if(!Objects.equals(dimension,dim)) { dimension=dim;cache.clear();candidates=List.of();frame=0; }
        if(frame==0) {
            List<BlockEntity> found=new ArrayList<>();
            int minX=(p.getBlockX()-16)>>4,maxX=(p.getBlockX()+16)>>4,minZ=(p.getBlockZ()-16)>>4,maxZ=(p.getBlockZ()+16)>>4;
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
                var chunk=world.getChunkManager().getWorldChunk(x,z);
                if(chunk!=null)for(var be:List.copyOf(chunk.getBlockEntities().values()))
                    if(!be.isRemoved() && be.getPos().getSquaredDistance(p.getPos())<=256)found.add(be);
            }
            found.sort(Comparator.comparingDouble(be->be.getPos().getSquaredDistance(p.getPos())));
            candidates=found.stream().limit(24).map(be->be.getPos().toImmutable()).toList();
            cache.keySet().retainAll(candidates);
        }
        int batch=(candidates.size()+3)/4;
        for(int i=frame*batch;i<Math.min(candidates.size(),(frame+1)*batch);i++) {
            BlockPos pos=candidates.get(i);
            if(pos.getSquaredDistance(p.getPos())>256) { cache.remove(pos);continue; }
            var chunk=world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);
            var be=chunk==null?null:chunk.getBlockEntities().get(pos);
            if(be==null || be.isRemoved()) { cache.remove(pos);continue; }
            String loot=be instanceof LootableInventory lootable && lootable.getLootTable()!=null?lootable.getLootTable().getValue().toString():null;
            var contents=be instanceof Inventory inv?ContainerReader.read(loot,inv.size(),slot->ItemCapture.read(inv.getStack(slot),slot)):new ContainerReader.Contents(false,List.of());
            var data=object("type",Registries.BLOCK_ENTITY_TYPE.getId(be.getType()).toString(),"x",pos.getX(),"y",pos.getY(),"z",pos.getZ(),
                "contents_known",contents.known(),"items",contents.known()?contents.items():null,"items_truncated",false);
            if(loot!=null)data.addProperty("loot_table",loot);
            cache.put(pos,new Cached(pos,Facts.fromJson(data)));
        }
        frame=(frame+1)%4;
        cache.values().removeIf(c->world.getChunkManager().getWorldChunk(c.pos().getX()>>4,c.pos().getZ()>>4)==null || c.pos().getSquaredDistance(p.getPos())>256);
        return cache.values().stream().sorted(Comparator.comparingDouble(c->c.pos().getSquaredDistance(p.getPos()))).map(c->{
            var data=c.facts().toJson();data.addProperty("distance",Math.round(Math.sqrt(c.pos().getSquaredDistance(p.getPos()))*10)/10.0);return Facts.fromJson(data);
        }).toList();
    }
}

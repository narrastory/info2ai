package com.example.info2ai.capture.server;
import com.example.info2ai.model.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.*;
import net.minecraft.world.World;
import java.util.*;
import java.time.Instant;
import static com.example.info2ai.capture.server.CaptureJson.*;
/** Large registries and stats are sampled once per second, independently of core state. */
public final class ProgressSection {
    private ProgressInfo cached;
    private int lastTick=Integer.MIN_VALUE;
    public ProgressInfo collect(MinecraftServer server,ServerPlayerEntity p) {
        if(cached!=null && server.getTicks()-lastTick<20)return cached;
        lastTick=server.getTicks();
        int done=0;boolean endVisited=false,netherVisited=false;
        record Completed(String id,String name,long when) { }
        List<Completed> completed=new ArrayList<>();
        // TODO(perf): 总采集冷启动/尖峰已超预算；注册表/进度遍历是待 profile 候选，Phase9 前用分段证据定位再优化。
        for(var entry:server.getAdvancementLoader().getAdvancements()) {
            var progress=p.getAdvancementTracker().getProgress(entry);
            if(!progress.isDone())continue;
            String id=entry.id().toString();
            if(id.equals("minecraft:story/enter_the_nether"))netherVisited=true;
            if(id.equals("minecraft:story/enter_the_end"))endVisited=true;
            if(entry.value().display().isEmpty())continue; // Recipe advancements are not milestones.
            done++;
            Instant when=null;
            for(String criterion:progress.getObtainedCriteria()) {
                var obtained=progress.getCriterionProgress(criterion).getObtainedTime();
                if(obtained!=null && (when==null || obtained.isAfter(when)))when=obtained;
            }
            if(when!=null)completed.add(new Completed(id,entry.value().display().get().getTitle().getString(),when.toEpochMilli()));
        }
        completed.sort(Comparator.comparingLong(Completed::when).reversed());
        long now=System.currentTimeMillis();
        List<Facts> recent=completed.stream().limit(10).map(c->facts("id",c.id(),"name",c.name(),"ago_s",Math.max(0,(now-c.when())/1000))).toList();
        int mined=0;for(var stat:Stats.MINED)mined+=p.getStatHandler().getStat(stat);
        var stats=facts("walked_cm",value(p,Stats.WALK_ONE_CM),"blocks_mined",mined,"deaths",value(p,Stats.DEATHS),"mob_kills",value(p,Stats.MOB_KILLS),
            "play_time_ticks",value(p,Stats.PLAY_TIME),"jumps",value(p,Stats.JUMP));
        int recipes=0;for(var recipe:server.getRecipeManager().values())if(p.getRecipeBook().contains(recipe))recipes++;
        var end=server.getWorld(World.END);
        Boolean dragon=end!=null && end.getEnderDragonFight()!=null?end.getEnderDragonFight().hasPreviouslyKilled():server.getSaveProperties().getDragonFight().previouslyKilled();
        if(p.getServerWorld().getRegistryKey().equals(World.END))endVisited=true;
        if(p.getServerWorld().getRegistryKey().equals(World.NETHER))netherVisited=true;
        cached=new ProgressInfo(done,recent,stats,recipes,dragon,endVisited,netherVisited,null);
        return cached;
    }
    private static int value(ServerPlayerEntity p,net.minecraft.util.Identifier id) { return p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(id)); }
}

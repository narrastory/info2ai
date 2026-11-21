package com.example.info2ai.events;
import java.util.*;
import com.example.info2ai.model.Signal;
import static com.example.info2ai.events.EventData.of;
/** 每四个游戏 tick 调用；未知条件不产生信号，消失条件立即清理计时。 */
public final class SignalEngine {
    private final Map<String,Long> sinceTick=new HashMap<>();
    private record Position(long tick,String dimension,double x,double z) {
    }
    private final ArrayDeque<Position> positions=new ArrayDeque<>();
    private Long sessionStart,miningStart;
    private Integer previousFood;
    public void reset() {
        sinceTick.clear();
        positions.clear();
        sessionStart=null;
        miningStart=null;
        previousFood=null;
    }
    public List<Signal> evaluate(Observation o,ActivityInference activity) {
        if(sessionStart==null)sessionStart=o.tick();
        if(o.tick()<sessionStart)reset();
        if(sessionStart==null)sessionStart=o.tick();
        List<Signal> out=new ArrayList<>();
        var v=o.vital();
        var e=o.env();
        var i=o.inv();
        var c=o.client();
        if(v!=null) {
            double pct=v.health()/v.maxHealth();
            if(pct<=.3)add(out,o,"low_health","warn",v.health()/2+" 颗心","health",v.health(),"pct",pct*100);
            if(pct<=.15)add(out,o,"critical_health","critical","剩 "+v.health()/2+" 颗心","health",v.health(),"pct",pct*100);
            if(v.food()<=6)add(out,o,"hungry","warn","饥饿值 "+v.food()+"/20","food",v.food());
            if(!o.dimension().equals("minecraft:overworld")&&pct<=.5)add(out,o,"dim_health_low","warn",
            "在 "+o.dimension()+"，生命值 "+Math.round(pct*100)+"%","health",v.health(),"pct",pct*100,"dimension",o.dimension());
            if(v.xpLevel()>=30)add(out,o,"xp_high","info","经验等级为 "+v.xpLevel(),"level",v.xpLevel());
            if(previousFood!=null&&v.food()<previousFood&&v.food()<15)add(out,o,"food_depleting","info","饥饿值正在下降","food",v.food(),"previous",previousFood);
            previousFood=v.food();
        }
        if(e!=null) {
            if(e.caveDepth()!=null&&e.caveDepth()>20&&Boolean.FALSE.equals(e.seeSky()))add(out,o,"below_surface","info","在地下 "+e.caveDepth()+" 格",
            "depth",e.caveDepth());
            long hostiles=o.mobs().stream().filter(Observation.Mob::hostile).count();
            if(e.blockLight()!=null&&e.blockLight()<=3&&hostiles>=1)add(out,o,"low_light","warn","光照低且附近有敌对生物","light",e.blockLight(),"hostile",hostiles);
            if(e.blockLight()!=null&&e.skyLight()!=null&&Math.max(e.blockLight(),
            e.skyLight())<=3&&i!=null&&Boolean.FALSE.equals(i.hasLight()))add(out,o,"no_light_source","info","这里很暗，背包没有光源","block_light",
            e.blockLight(),"sky_light",e.skyLight());
            if(e.dayTime()>=13000&&e.dayTime()<23000&&i!=null&&Boolean.FALSE.equals(i.hasBed())&&Boolean.FALSE.equals(e.bedNearby()))add(out,o,
            "no_bed","warn","夜晚，背包和已检查附近区域没有床","day_time",e.dayTime());
            if(e.biome()!=null&&Set.of("minecraft:mushroom_fields","minecraft:cherry_grove","minecraft:deep_dark",
            "minecraft:warm_ocean").contains(e.biome()))add(out,o,"biome_notable","info","当前群系为"+com.example.info2ai.util.NameZh.get(e.biome()),
            "id",e.biome());
            if(e.fallHeight()!=null&&e.fallHeight()>4&&Boolean.FALSE.equals(e.groundSupport()))add(out,o,"fall_risk","warn","脚下没有支撑","height",e.fallHeight());
            if(o.firstBiomeKnown())add(out,o,"first_time_biome","info","历史来源确认首次进入该群系","id",e.biome());
        }
        o.mobs().stream().filter(m->m.distance()<10&&m.targeting()).min(Comparator.comparingDouble(Observation.Mob::distance)).ifPresent(m->add(out,o,
        "dangerous_mobs","warn",m.id()+" 正以玩家为目标","entity",m.id(),"dist",m.distance()));
        if(i!=null) {
            if(i.free()==0)add(out,o,"inventory_full","warn","背包满了","free",0);
            if(i.maxDurability()>0&&i.durability()/(double)i.maxDurability()<.15)add(out,o,"tool_low","warn","工具耐久较低","tool",i.heldId(),"remaining",
            i.durability(),"max",i.maxDurability());
            if(Boolean.FALSE.equals(i.hasFood())&&v!=null&&v.food()<15)add(out,o,"no_food","warn","背包里没有食物","food",v.food());
            if(i.spawnDistance()!=null&&i.spawnDistance()>2000)add(out,o,"far_from_spawn","info","离同维度出生点 "+Math.round(i.spawnDistance())+" 格",
            "distance",i.spawnDistance());
        }
        if(o.tick()-sessionStart>216000)add(out,o,"long_session","info","本次游戏已超过三小时","uptime_s",(o.tick()-sessionStart)/20);
        if(c!=null) {
            if(c.localHour()!=null&&c.localHour()>=0&&c.localHour()<6)add(out,o,"late_night_irl","info","现实时间在凌晨0至6点之间","hour",c.localHour());
            if(c.idleTicks()!=null&&c.idleTicks()>1800)add(out,o,"afk","info",c.idleTicks()/20+" 秒没有输入或移动","idle_s",c.idleTicks()/20);
        }
        if(o.structuresKnown()) {
            o.structures().stream().filter(s->s.distance()<150)
                .min(Comparator.comparingDouble(Observation.Structure::distance))
                .ifPresent(s->add(out,o,"structure_nearby","info",
                    s.distance()+(s.source()==null?" 格外有":" 格外预测有")+com.example.info2ai.util.NameZh.get(s.displayId()),
                    "id",s.id(),"dist",s.distance(),"dir",s.direction(),"key",s.key(),"dimension",o.dimension(),
                    "x",s.x(),"z",s.z(),"source",s.source(),"confidence",s.confidence(),"absolute_first_known",false,
                    "type",s.type(),"structure_id",s.id(),"variant_status",s.variantStatus()));
        }
        if(activity!=null&&activity.miningCount()>120&&activity.miningActiveAt(o.tick())) {
            miningStart=activity.miningSince();
            if(o.tick()-miningStart>6000)add(out,o,"mining_long","info","持续观察到挖矿","minutes",(o.tick()-miningStart)/1200d,"blocks",activity.miningCount(),
                "idle_timeout_ticks",ActivityInference.MINING_IDLE_TIMEOUT_TICKS,"last_break_ago_ticks",o.tick()-activity.lastMiningBreak());
        } else miningStart=null;
        while(!positions.isEmpty()&&(o.tick()-positions.getFirst().tick()>6000||!positions.getFirst().dimension().equals(o.dimension())))positions.removeFirst();
        if(!positions.isEmpty()&&o.tick()-positions.getFirst().tick()>=6000&&c!=null&&c.idleTicks()!=null&&c.idleTicks()<=1800) {
            var start=positions.getFirst();
            double moved=Math.hypot(o.pos().get(0)-start.x(),o.pos().get(2)-start.z());
            if(moved<32)add(out,o,"stuck","info","五分钟内位移小于32格","displacement",moved,"window_s",300);
        }
        positions.addLast(new Position(o.tick(),o.dimension(),o.pos().get(0),o.pos().get(2)));
        Set<String> active=new HashSet<>();
        out.forEach(s->active.add(s.kind()));
        sinceTick.keySet().retainAll(active);
        return List.copyOf(out);
    }
    private void add(List<Signal> out,Observation o,String kind,String severity,String note,Object... details) {
        long start=sinceTick.computeIfAbsent(kind,k->o.tick());
        out.add(new Signal(kind,severity,note,(int)Math.max(0,(o.tick()-start)/20),of(details)));
    }
}

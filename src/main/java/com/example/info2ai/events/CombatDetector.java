package com.example.info2ai.events;
import java.util.*;
import static com.example.info2ai.events.Observation.*;
public final class CombatDetector implements EventDetector {
    private Map<String,Double> bosses=Map.of();
    public String kind() {
        return "combat";
    }
    public void tick(EventContext c) {
        var n=c.current();
        var p=c.previous();
        for(var a:n.actions())if(a.confirmed()) {
            if(a.type()==ActionType.KILL && !"player".equals(a.id()))c.action("mob_killed","info","击杀了 "+a.id(),a,"entity",a.id(),"source",a.source(),
            "attacker",a.attacker());
            if(a.type()==ActionType.ATTACK&&a.amount()>0)c.action("player_attacked_mob","info","对 "+a.id()+" 造成 "+a.amount()+" 点伤害",a,"entity",a.id(),
            "amount",a.amount(),"source",a.source());
        }
        if(p!=null&&p.mobsKnown()&&n.mobsKnown()) {
            long count=n.mobs().stream().filter(Mob::hostile).count();
            if(p.mobs().stream().noneMatch(Mob::hostile)&&count>=3)c.emit("hostiles_nearby","warn","附近有 "+count+" 只敌对生物","n",count);
            boolean creeper=n.mobs().stream().anyMatch(m->m.id().equals("minecraft:creeper")&&m.distance()<8);
            if(creeper&&p.mobs().stream().noneMatch(m->m.id().equals("minecraft:creeper")&&m.distance()<8))c.emit("creeper_nearby","warn","8 格内有苦力怕","range",8);
            for(var mob:n.mobs()) {
                boolean previouslyTargeting=p.mobs().stream().anyMatch(old->old.uuid().equals(mob.uuid())&&old.targeting());
                if(mob.targeting()&&!previouslyTargeting) {
                    c.emit("entity_targeting_player","warn",mob.id()+" 正以玩家为目标",
                        "entity",mob.id(),"dist",mob.distance(),"uuid",mob.uuid());
                }
            }
        }
        if(!n.bossesKnown())return;
        Map<String,Double> next=new HashMap<>();
        for(var b:n.bosses()) {
            Double old=bosses.get(b.uuid());
            if(old==null && p!=null&&p.bossesKnown())c.emit("boss_engaged","critical",b.name()+" 的 Boss 血条出现","boss",b.name(),"id",b.uuid(),"pct",
            b.percent()*100);
            if(old!=null)for(double threshold:new double[] {
                .75,.5,.25,.1
            } )if(old>threshold&&b.percent()<=threshold)c.emit("boss_health_threshold","info",b.name()+" 血条降到 "+Math.round(threshold*100)+"% 以下",
            "boss",b.name(),"id",b.uuid(),"pct",b.percent()*100,"threshold",threshold*100);
            next.put(b.uuid(),b.percent());
        }
        bosses=next;
    }
}

package com.example.info2ai.events;
import java.util.*;
import static com.example.info2ai.events.Observation.*;
public final class SurvivalDetector implements EventDetector {
    private boolean low;
    public String kind() {
        return "survival";
    }
    public void tick(EventContext c) {
        var n=c.current().vital();
        var p=c.previous()==null?null:c.previous().vital();
        if(n==null)return;
        boolean damageFact=false;
        for(var a:c.current().actions())if(a.confirmed() && a.type()==ActionType.DAMAGE && a.amount()>0) {
            damageFact=true;
            c.action("player_damaged","warn","受到 "+a.amount()+" 点伤害（"+a.source()+"）",a,"amount",a.amount(),"source",a.source(),"attacker",
            a.attacker(),"health",n.health(),"applied",a.details());
            if("minecraft:fall".equals(a.source()))c.action("took_fall_damage","warn","受到摔落伤害",a,"blocks",
            a.details()==null?null:com.example.info2ai.util.JsonUtil.number(a.details().toJson(),"blocks"),"amount",a.amount());
        }
        if(p==null) {
            low=n.health()/n.maxHealth()<=.3;
            return;
        }
        if(!damageFact && n.health()<p.health())c.emit("player_damaged","warn","生命减少了 "+(p.health()-n.health())+" 点","amount",p.health()-n.health(),
        "source",null,"attacker",null,"health",n.health());
        if(n.health()-p.health()>=1)c.emit("player_healed","info","恢复了 "+(n.health()-p.health())+" 点生命","amount",n.health()-p.health(),"health",n.health());
        double pct=n.health()/n.maxHealth();
        if(!low && pct<=.3) {
            low=true;
            c.emit("low_health_entered","critical","生命值剩 "+Math.round(pct*100)+"%","health",n.health(),"pct",pct*100);
        }
        if(low && pct>.5) {
            low=false;
            c.emit("low_health_exited","info","生命值回到 "+Math.round(pct*100)+"%","health",n.health(),"pct",pct*100);
        }
        if(n.health()<=3 && n.health()>0 && p.health()>3)c.emit("near_death","critical","生命值剩 "+n.health()/2+" 颗心","health",n.health());
        if(n.dead() && !p.dead()) {
            var death=c.current().actions().stream().filter(a->a.type()==ActionType.KILL && "player".equals(a.id())).findFirst().orElse(null);
            String cause=death==null?null:death.source();
            String biome=c.current().env()==null?null:c.current().env().biome();
            c.emit("death","critical",
            "死亡（"+(cause==null?"死因未知":cause)+"），群系 "+(biome==null?"未知":com.example.info2ai.util.NameZh.get(biome))+"，坐标 "+c.current().pos(),
            "cause",cause,"pos",c.current().pos(),"biome",
            c.current().env()==null?null:c.current().env().biome(),"item_loss",null);
        }
        if(!n.dead() && p.dead())c.emit("respawned","info","在 "+c.current().pos()+" 重生（"+c.current().dimension()+"）","pos",c.current().pos(),
        "dimension",c.current().dimension());
        if(n.lava()&&!p.lava())c.emit("fell_in_lava","critical","进入岩浆","pos",c.current().pos());
        if(n.fire()&&!p.fire())c.emit("on_fire","warn","身上着火了","pos",c.current().pos());
        if(n.submerged()&&n.air()<100&&(!p.submerged()||p.air()>=100))c.emit("drowning","warn","氧气剩 "+n.air(),"air",n.air());
        if(n.food()<=6&&p.food()>6)c.emit("hungry","warn","饥饿值剩 "+n.food()+"/20","food",n.food());
        if(n.food()==0&&p.food()>0)c.emit("starving","warn","饥饿值为零","health",n.health(),"food",0);
        for(var effect:n.effects().entrySet())if(!p.effects().containsKey(effect.getKey())) {
            String duration=effect.getValue()<0?"无限时长":effect.getValue()+" 秒";
            c.emit("effect_gained","info","获得 "+com.example.info2ai.util.NameZh.get(effect.getKey())+"（"+duration+"）",
                "id",effect.getKey(),"duration_s",effect.getValue());
        }
        for(String id:p.effects().keySet())if(!n.effects().containsKey(id))c.emit("effect_lost","info",id+" 效果结束","id",id);
    }
}

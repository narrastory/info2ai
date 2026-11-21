package com.example.info2ai.events;
import java.util.*;
import static com.example.info2ai.events.Observation.*;
import com.example.info2ai.util.NameZh;
public final class ExplorationDetector implements EventDetector {
    private final Set<String> structures=new HashSet<>();
    private Double high,low;
    private Boolean cave;
    public String kind() {
        return "exploration";
    }
    public void tick(EventContext c) {
        var n=c.current();
        var p=c.previous();
        if(high==null) {
            high=n.pos().get(1);
            low=high;
        }
        if(p!=null && !Objects.equals(n.dimension(),p.dimension())) {
            c.emit("dimension_changed","info","进入 "+n.dimension(),"dimension",n.dimension());
            if("minecraft:the_end".equals(n.dimension()))c.emit("end_enter","info","进入末地","dimension",n.dimension());
            high=n.pos().get(1);
            low=high;
        }
        if(n.pos().get(1)-high>32) {
            high=n.pos().get(1);
            c.emit("altitude_record","info","升到 y="+high,"y",high,"direction","up");
        }
        if(low-n.pos().get(1)>32) {
            low=n.pos().get(1);
            c.emit("altitude_record","info","下潜到 y="+low,"y",low,"direction","down");
        }
        if(p!=null && n.env()!=null && p.env()!=null) {
            var a=n.env();
            var b=p.env();
            if(a.biome()!=null&&b.biome()!=null&&!a.biome().equals(b.biome()))c.emit("biome_changed","info","进入"+NameZh.get(a.biome()),"id",a.biome());
            if(Boolean.FALSE.equals(b.seeSky())&&Boolean.TRUE.equals(a.seeSky()))c.emit("reached_surface","info","现在能看见天空","can_see_sky",true);
        }
        if(n.env()!=null&&n.env().enclosed()!=null) {
            var env=n.env();
            boolean outside=env.enclosed()<.4;
            boolean inside=env.enclosed()>.7&&env.skyLight()!=null&&env.skyLight()<5;
            if(cave==null) {
                if(outside)cave=false;else if(inside)cave=true;
            } else if(!cave&&inside) {
                cave=true;c.emit("entered_cave","info","进入封闭地下区域","cave_depth",env.caveDepth(),"enclosed_ratio",env.enclosed());
            } else if(cave&&outside) {
                cave=false;c.emit("exited_cave","info","离开封闭地下区域","enclosed_ratio",env.enclosed());
            }
        }
        if(p!=null && n.vital()!=null && p.vital()!=null) {
            var a=n.vital();
            var b=p.vital();
            if(a.sleeping()&&!b.sleeping())c.emit("sleep_started","info","开始睡觉","day",n.env()==null?null:(n.env().worldTime()/24000+1));
            if(!a.sleeping()&&b.sleeping())c.emit("woke_up","info","醒来了","day_time",n.env()==null?null:n.env().dayTime());
            if(!Objects.equals(a.mount(),b.mount())) {
                if(a.mount()!=null)c.emit("vehicle_mounted","info","骑上了"+NameZh.get(a.mount()),"entity",a.mount());
                else c.emit("vehicle_dismounted","info","结束骑乘","entity",b.mount());
            }
        }
        if(n.structuresKnown())for(var s:n.structures())if(s.distance()<=128&&!Boolean.TRUE.equals(s.visited())&&structures.add(n.dimension()+":"+s.key()))
            c.emitAt("structure_found","info",(s.source()==null?"发现":"接近预测的")+NameZh.get(s.displayId()),0,n.pos(),s.facts(n.dimension()));
        for(var a:n.actions())if(a.confirmed()) {
            if(a.type()==ActionType.PORTAL)c.action("portal_built","info","观察到下界传送门方块出现",a,"id",a.id());
            if(a.type()==ActionType.SLEEP_COMPLETE)c.action("slept","info","睡眠结束，游戏时间跳到清晨",a,"day_time",n.env()==null?null:n.env().dayTime());
        }
    }
}

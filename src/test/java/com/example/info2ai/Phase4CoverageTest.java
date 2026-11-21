package com.example.info2ai;
import com.example.info2ai.events.*;
import com.example.info2ai.events.Observation.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.example.info2ai.Phase4RulesTest.*;
public class Phase4CoverageTest {
    @Test public void combatUsesConfirmedDeathsAndDistinctBossIds() {
        var b=baseline(0);
        EventEngine engine=new EventEngine();
        engine.tick(b,0);
        var mobs=List.of(new Mob("a","minecraft:creeper",7,true,true),new Mob("b","minecraft:zombie",9,true,false),new Mob("c","minecraft:skeleton",9,
        true,false));
        var boss=List.of(new Boss("boss1","Boss",1),new Boss("boss2","Boss",1));
        var n=new Observation(1,b.playerId(),b.dimension(),b.pos(),b.vital(),b.env(),b.inv(),null,List.of(action(ActionType.ATTACK,
        "minecraft:zombie")),mobs,boss,List.of(),false,false);
        assertTrue(kinds(engine.tick(n,1)).containsAll(Set.of("hostiles_nearby","creeper_nearby","entity_targeting_player","player_attacked_mob",
        "boss_engaged")));
        n=new Observation(2,b.playerId(),b.dimension(),b.pos(),b.vital(),b.env(),b.inv(),null,List.of(),List.of(),List.of(new Boss("boss1","Boss",.1),
        new Boss("boss2","Boss",1)),List.of(),false,false);
        var crossed=engine.tick(n,2).stream().filter(x->x.kind().equals("boss_health_threshold")).toList();
        assertEquals(4,crossed.size());
        assertFalse(kinds(engine.tick(baseline(3),3)).contains("mob_killed"));
    }
    @Test public void movementAndRecoveryEventsKeepTheirSeparateTransitions() {
        var b=baseline(0);
        EventEngine engine=new EventEngine();
        engine.tick(frame(0,vital(3,0,1,100,false,true,Map.of("minecraft:speed",10),"minecraft:horse"),env(23999,"minecraft:deep_dark",.8,0,false),
        b.inv(),null,List.of()),0);
        var n=new Observation(1,b.playerId(),"minecraft:the_end",List.of(0d,64d,0d),b.vital(),env(24000,"minecraft:plains",.2,15,true),b.inv(),null,
        List.of(),List.of(),List.of(),List.of(),false,false);
        var out=kinds(engine.tick(n,11000));
        assertTrue(out.containsAll(Set.of("dimension_changed","end_enter","player_healed","effect_lost","vehicle_dismounted","exited_cave","reached_surface")));
        n=new Observation(2,b.playerId(),"minecraft:the_end",List.of(0d,97d,0d),b.vital(),n.env(),b.inv(),null,List.of(),List.of(),List.of(),
        List.of(),false,false);
        assertTrue(kinds(engine.tick(n,11001)).contains("altitude_record"));
    }
    @Test public void fullInventoryLootAndToolBoundaryAreObservedFacts() {
        var b=baseline(0);
        EventEngine engine=new EventEngine();
        engine.tick(b,0);
        var inv=new Inventory(0,"minecraft:iron_pickaxe",14,100,true,true,false,null,List.of("minecraft:diamond"));
        var n=frame(1,b.vital(),b.env(),inv,null,List.of(action(ActionType.OPEN,"minecraft:chest"),action(ActionType.DROP,"minecraft:cobblestone")));
        assertTrue(kinds(engine.tick(n,1)).containsAll(Set.of("inventory_full","tool_low","chest_opened","chest_loot_found","inventory_full_lost")));
    }
    @Test public void afkEdgesAndWeatherDoNotReplayWhileUnchanged() {
        var b=baseline(0);
        EventEngine engine=new EventEngine();
        engine.tick(b,0);
        var env=new Environment("minecraft:plains",.2,15,15,true,0d,13000,13000,true,true,"world",false,null,null);
        var inv=new Inventory(1,null,0,0,true,true,false,null,List.of());
        var n=frame(1,vital(20,20,1,100,false,false,Map.of(),null),env,inv,new Client(null,1801L,null,12),List.of());
        assertTrue(kinds(engine.tick(n,1)).containsAll(Set.of("afk_started","weather_changed","night_fell","no_bed_at_night","phantom_risk")));
        assertTrue(kinds(engine.tick(frame(2,n.vital(),env,inv,new Client(null,1802L,null,12),List.of()),2)).isEmpty());
        assertTrue(kinds(engine.tick(frame(3,n.vital(),env,inv,new Client(null,0L,null,12),List.of()),3)).contains("afk_ended"));
    }
    @Test public void remainingSignalWindowsUseExactGameTimeAndClear() {
        var b=baseline(0);
        SignalEngine signals=new SignalEngine();
        signals.evaluate(frame(0,b.vital(),null,null,new Client(null,0L,null,12),List.of()),null);
        for(int tick=4;tick<=6000;tick+=4)signals.evaluate(frame(tick,b.vital(),null,null,new Client(null,0L,null,12),List.of()),null);
        assertTrue(signals.evaluate(frame(6000,b.vital(),null,null,new Client(null,0L,null,12),List.of()),null).stream().anyMatch(s->s.kind().equals("stuck")));
        var lower=new Vital(20,20,14,300,false,false,false,false,false,1,100,0,Map.of(),null,"");
        assertTrue(signals.evaluate(frame(216001,lower,null,null,null,List.of()),
        null).stream().map(s->s.kind()).toList().containsAll(List.of("long_session","food_depleting")));
        assertFalse(signals.evaluate(frame(216005,lower,null,null,null,List.of()),null).stream().anyMatch(s->s.kind().equals("food_depleting")));
    }
    @Test public void missingMobAndBossSourcesCannotPretendAnEmptyBaseline() {
        var b=baseline(0);
        EventEngine engine=new EventEngine();
        engine.tick(new Observation(0,b.playerId(),b.dimension(),b.pos(),b.vital(),b.env(),b.inv(),null,List.of(),List.of(),List.of(),List.of(),false,
        false,false,false),0);
        var n=new Observation(1,b.playerId(),b.dimension(),b.pos(),b.vital(),b.env(),b.inv(),null,List.of(),List.of(new Mob("a","minecraft:zombie",8,
        true,true),new Mob("b","minecraft:skeleton",8,true,false),new Mob("c","minecraft:creeper",7,true,false)),List.of(new Boss("a","boss",1)),
        List.of(),false,false,true,true);
        var out=kinds(engine.tick(n,1));
        assertFalse(out.contains("hostiles_nearby"));
        assertFalse(out.contains("boss_engaged"));
        assertFalse(out.contains("entity_targeting_player"));
    }
    @Test public void advancementTitleAndEffectDurationUseAlreadyObservedValues() {
        var b=baseline(0);EventEngine engine=new EventEngine();engine.tick(b,0);
        var advancement=new Action(ActionType.ADVANCEMENT,"minecraft:story/mine_stone",1,0,null,"p",true,true,false,false,null,
            EventData.of("name","石器时代"));
        var n=frame(1,vital(20,20,1,100,false,false,Map.of("minecraft:speed",12),null),b.env(),b.inv(),null,List.of(advancement));
        var out=engine.tick(n,1);
        var progress=out.stream().filter(e->e.kind().equals("advancement")).findFirst().orElseThrow();
        assertTrue(progress.text().contains("石器时代"));
        assertEquals("minecraft:story/mine_stone",progress.data().toJson().get("id").getAsString());
        assertEquals("石器时代",progress.data().toJson().get("name").getAsString());
        var effect=out.stream().filter(e->e.kind().equals("effect_gained")).findFirst().orElseThrow();
        assertTrue(effect.text().contains("12 秒"));
        assertEquals(12,effect.data().toJson().get("duration_s").getAsInt());
    }

}

package com.example.info2ai;
import com.example.info2ai.events.*;
import com.example.info2ai.events.Observation.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class Phase4RulesTest {
    static Vital vital(double hp,int food,int level,int xp,boolean dead,boolean sleep,Map<String,Integer> effects,String mount) {
        return new Vital(hp,20,food,99,true,true,true,dead,sleep,level,xp,72000,effects,mount,"armor");
    }
    static Environment env(long time,String biome,Double ratio,int sky,boolean see) {
        return new Environment(biome,ratio,sky,3,see,30d,time%24000,time,false,false,"world",null,5d,false);
    }
    static Observation frame(long tick,Vital vital,Environment env,Inventory inv,Client client,List<Action> actions) {
        return new Observation(tick,"player","minecraft:overworld",List.of(0d,64d,0d),vital,env,inv,client,actions,List.of(),List.of(),List.of(),false,false);
    }
    static Observation baseline(long tick) {
        return frame(tick,new Vital(20,20,20,300,false,false,false,false,false,1,100,0,Map.of(),null,"armor"),env(12000,"minecraft:plains",.2,15,
        true),new Inventory(1,"minecraft:iron_pickaxe",15,100,true,true,true,null,List.of()),null,List.of());
    }
    static Set<String> kinds(List<EventDraft> d) {
        Set<String> r=new HashSet<>();
        d.forEach(x->r.add(x.kind()));
        return r;
    }
    static Action action(ActionType type,String id) {
        return new Action(type,id,1,2,"minecraft:fall","player",true,true,true,false,null,EventData.of("blocks",8));
    }
    @Test public void survivalEdgesCarryFactsAndNoInitialReplay() {
        EventEngine e=new EventEngine();
        assertTrue(e.tick(baseline(0),0).isEmpty());
        var midpoint=baseline(0);
        e.tick(frame(1,midpoint.vital(),env(12001,"minecraft:plains",.5,3,false),midpoint.inv(),null,List.of()),1);
        var n=frame(2,vital(3,0,1,100,false,true,Map.of("minecraft:speed",12),"minecraft:horse"),env(13000,"minecraft:deep_dark",.8,0,false),
        baseline(0).inv(),null,List.of(action(ActionType.DAMAGE,"player")));
        var result=e.tick(n,1);
        assertTrue(kinds(result).containsAll(Set.of("player_damaged","near_death","hungry","starving","drowning","fell_in_lava","on_fire",
        "effect_gained","sleep_started","vehicle_mounted","entered_cave","biome_changed","night_fell")));
        var fall=result.stream().filter(x->x.kind().equals("took_fall_damage")).findFirst().orElseThrow();
        assertEquals(8,fall.data().toJson().get("blocks").getAsInt());
        assertTrue(e.tick(n,2).isEmpty());
    }
    @Test public void everyActionOutcomeHasTypedData() {
        EventEngine e=new EventEngine();
        e.tick(baseline(0),0);
        var a=List.of(action(ActionType.PLACE,"minecraft:crafting_table"),action(ActionType.TOOL_BREAK,"minecraft:iron_pickaxe"),
        action(ActionType.OPEN,"minecraft:chest"),action(ActionType.ENCHANT,"minecraft:diamond_sword"),action(ActionType.ADVANCEMENT,
        "minecraft:story/mine_stone"),action(ActionType.RECIPE,"recipe"),action(ActionType.CRAFT,"minecraft:torch"),action(ActionType.PORTAL,
        "minecraft:nether_portal"),action(ActionType.SLEEP_COMPLETE,"sleep"));
        var result=e.tick(frame(1,baseline(0).vital(),baseline(0).env(),baseline(0).inv(),null,a),1);
        assertTrue(kinds(result).containsAll(Set.of("placed_significant","tool_broke","chest_opened","enchant_done","advancement","recipe_unlocked",
        "item_crafted","portal_built","slept")));
        assertTrue(result.stream().allMatch(x->x.tick()==1&&x.pos().equals(List.of(0d,64d,0d))&&x.data()!=null));
    }
    @Test public void unknownAbsencesCannotCreateClaims() {
        SignalEngine s=new SignalEngine();
        var o=frame(0,baseline(0).vital(),null,null,null,List.of());
        assertTrue(s.evaluate(o,null).isEmpty());
        var n=frame(4,new Vital(20,20,14,300,false,false,false,false,false,0,0,0,Map.of(),null,""),env(13000,"minecraft:plains",null,0,false),
        new Inventory(1,null,0,0,null,null,null,null,List.of()),null,List.of());
        var kinds=s.evaluate(n,null).stream().map(x->x.kind()).toList();
        assertFalse(kinds.contains("no_food"));
        assertFalse(kinds.contains("no_bed"));
        assertFalse(kinds.contains("afk"));
        assertFalse(kinds.contains("no_light_source"));
        assertFalse(kinds.contains("first_time_biome"));
    }
    @Test public void playerIdentityResetPreventsDeathReplay() {
        EventEngine e=new EventEngine();
        e.tick(baseline(0),0);
        var n=baseline(1);
        var other=new Observation(1,"other",n.dimension(),n.pos(),vital(0,0,1,100,true,false,Map.of(),null),n.env(),n.inv(),null,List.of(),List.of(),
        List.of(),List.of(),false,false);
        assertFalse(kinds(e.tick(other,1)).contains("death"));
    }
    @Test public void attackExpiresAtTenSecondsAndPauseDoesNotAgeWindow() {
        ActivityInference a=new ActivityInference();
        a.observe(baseline(0));
        var n=baseline(1);
        a.observe(frame(1,n.vital(),n.env(),n.inv(),new Client("enchanting",1801L,null,12),List.of(action(ActionType.ATTACK,"minecraft:zombie"))));
        assertEquals("fighting",a.current().kind());
        a.observe(baseline(1));
        assertEquals("fighting",a.current().kind());
        a.observe(baseline(200));
        assertEquals("fighting",a.current().kind());
        a.observe(baseline(201));
        assertEquals("idle",a.current().kind());
    }
    @Test public void structureAndFirstBiomeNeedProvenance() {
        var n=baseline(0);
        var o=new Observation(0,n.playerId(),n.dimension(),n.pos(),n.vital(),n.env(),n.inv(),null,List.of(),List.of(),List.of(),
        List.of(new Structure("village1","minecraft:village_plains",127,"N")),true,true);
        EventEngine e=new EventEngine();
        assertTrue(kinds(e.tick(o,0)).contains("structure_found"));
        assertFalse(kinds(e.tick(o,1)).contains("structure_found"));
        assertTrue(new SignalEngine().evaluate(o,null).stream().map(x->x.kind()).toList().containsAll(List.of("structure_nearby","first_time_biome")));
    }
    @Test public void timeWrapAndSleepCancellationRemainDistinct() {
        EventEngine e=new EventEngine();
        var b=baseline(0);
        e.tick(frame(0,vital(20,20,1,100,false,true,Map.of(),null),env(23999,"minecraft:plains",.2,15,true),b.inv(),null,List.of()),0);
        var result=e.tick(frame(1,b.vital(),env(24000,"minecraft:plains",.2,15,true),b.inv(),null,List.of()),1);
        assertTrue(kinds(result).containsAll(Set.of("dawn","woke_up")));
        assertFalse(kinds(result).contains("slept"));
    }
    @Test public void deathXpLossOnlyAfterRespawn() {
        EventEngine e=new EventEngine();
        e.tick(baseline(0),0);
        var b=baseline(0);
        assertTrue(kinds(e.tick(frame(1,vital(0,20,1,100,true,false,Map.of(),null),b.env(),b.inv(),null,List.of(action(ActionType.KILL,"player"))),
        1)).contains("death"));
        var result=e.tick(frame(2,new Vital(20,20,20,300,false,false,false,false,false,0,0,0,Map.of(),null,"armor"),b.env(),b.inv(),null,List.of()),2);
        assertTrue(kinds(result).containsAll(Set.of("respawned","xp_lost")));
    }
    @Test public void signalThresholdsAreStrictAndDataNumeric() {
        var b=baseline(0);
        var i=new Inventory(0,"minecraft:iron_pickaxe",14,100,false,false,false,2001d,List.of());
        var o=frame(0,vital(3,6,30,100,false,false,Map.of(),null),env(13000,"minecraft:deep_dark",.8,0,false),i,new Client(null,1801L,null,1),List.of());
        o=new Observation(0,o.playerId(),"minecraft:the_nether",o.pos(),o.vital(),new Environment("minecraft:deep_dark",.8,0,3,false,30d,13000,13000,
        false,false,"world",false,5d,false),i,o.client(),List.of(),List.of(new Mob("a","minecraft:zombie",9,true,true)),List.of(),List.of(),false,false);
        var out=new SignalEngine().evaluate(o,null);
        var kinds=out.stream().map(x->x.kind()).toList();
        assertTrue(kinds.containsAll(List.of("low_health","critical_health","hungry","below_surface","low_light","dangerous_mobs","no_light_source",
        "inventory_full","tool_low","no_food","far_from_spawn","late_night_irl","no_bed","biome_notable","afk","dim_health_low","fall_risk","xp_high")));
        assertEquals(3,out.stream().filter(x->x.kind().equals("low_health")).findFirst().orElseThrow().detail().toJson().get("health").getAsInt());
    }
}

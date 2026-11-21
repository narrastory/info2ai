package com.example.info2ai;
import com.example.info2ai.events.*;
import com.example.info2ai.events.Observation.*;
import com.example.info2ai.model.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class Phase4EngineTest {
    private static Observation frame(long tick, double hp, int food, List<Action> actions) {
        return new Observation(tick,"p","minecraft:overworld",List.of(0d,64d,0d),
        new Vital(hp,20,food,300,false,false,false,hp==0,false,0,0,0,Map.of(),null,""),
        null,null,null,actions,List.of(),List.of(),List.of(),false,false);
    }
    private static List<String> kinds(List<EventDraft> events) {
        return events.stream().map(EventDraft::kind).toList();
    }
    private static Action action(ActionType type,String id,int count,boolean success,boolean attributed) {
        return new Action(type,id,count,3,"minecraft:fall",null,success,attributed,true,false,null,null);
    }
    @Test public void damageThresholdCooldownAndImmutableSuppressionCount() {
        EventEngine e=new EventEngine();
        e.tick(frame(0,20,20,List.of()),0);
        var first=e.tick(frame(1,19,20,List.of()),0);
        assertTrue(kinds(first).contains("player_damaged"));
        assertFalse(kinds(e.tick(frame(2,18,20,List.of()),499)).contains("player_damaged"));
        var second=e.tick(frame(3,17,20,List.of()),500).stream().filter(x->x.kind().equals("player_damaged")).findFirst().orElseThrow();
        assertEquals(2,second.data().toJson().get("count").getAsInt());
        assertEquals(1,first.stream().filter(x->x.kind().equals("player_damaged")).findFirst().orElseThrow().data().toJson().get("count").getAsInt());
    }
    @Test public void lowHealthHysteresisAndNewPlayerReset() {
        EventEngine e=new EventEngine();
        e.tick(frame(0,20,20,List.of()),0);
        assertTrue(kinds(e.tick(frame(1,6,20,List.of()),1)).contains("low_health_entered"));
        assertFalse(kinds(e.tick(frame(2,9,20,List.of()),11000)).contains("low_health_exited"));
        assertTrue(kinds(e.tick(frame(3,10.01,20,List.of()),11001)).contains("low_health_exited"));
        e.reset();
        assertFalse(kinds(e.tick(frame(0,20,20,List.of()),12000)).contains("player_healed"));
    }
    @Test public void authoritativeSuccessAndAttributionRequiredForRareBreakAndKill() {
        EventEngine e=new EventEngine();
        e.tick(frame(0,20,20,List.of()),0);
        assertFalse(kinds(e.tick(frame(1,20,20,List.of(action(ActionType.BREAK,"minecraft:diamond_ore",1,false,true),action(ActionType.KILL,
        "minecraft:zombie",1,true,false))),1)).contains("mob_killed"));
        var out=e.tick(frame(2,20,20,List.of(action(ActionType.BREAK,"minecraft:diamond_ore",1,true,true),action(ActionType.KILL,"minecraft:zombie",1,
        true,true))),2);
        assertTrue(kinds(out).containsAll(List.of("block_broken_rare","ore_found","mob_killed")));
        assertFalse(kinds(e.tick(frame(3,20,20,List.of()),1000)).contains("mob_killed"));
    }
    @Test public void miningDroughtAndStreakExactBoundary() {
        EventEngine e=new EventEngine();
        e.tick(frame(0,20,20,List.of()),0);
        assertFalse(kinds(e.tick(frame(1,20,20,List.of(action(ActionType.BREAK,"minecraft:stone",119,true,true))),1)).contains("mining_drought"));
        assertTrue(kinds(e.tick(frame(2,20,20,List.of(action(ActionType.BREAK,"minecraft:stone",1,true,true))),2)).contains("mining_drought"));
        assertTrue(kinds(e.tick(frame(3,20,20,List.of(action(ActionType.BREAK,"minecraft:stone",80,true,true))),3)).contains("mining_streak"));
        var base=frame(4,20,20,List.of());
        var effect=new Vital(20,20,20,300,false,false,false,false,false,0,0,0,Map.of("minecraft:speed",10),null,"");
        e.tick(new Observation(4,base.playerId(),base.dimension(),base.pos(),effect,null,null,null,List.of(),List.of(),List.of(),List.of(),false,false),300001);
        var interrupted=new Observation(5,base.playerId(),base.dimension(),base.pos(),effect,null,null,null,List.of(action(ActionType.BREAK,
            "minecraft:stone",20,true,true)),List.of(),List.of(),List.of(),false,false);
        assertFalse(kinds(e.tick(interrupted,300004)).contains("mining_streak"));
    }
    @Test public void signalSinceRemovalAndReset() {
        SignalEngine e=new SignalEngine();
        assertEquals(0,e.evaluate(frame(0,6,6,List.of()),null).stream().filter(s->s.kind().equals("low_health")).findFirst().orElseThrow().sinceS().intValue());
        assertEquals(5,e.evaluate(frame(100,6,6,List.of()),
        null).stream().filter(s->s.kind().equals("low_health")).findFirst().orElseThrow().sinceS().intValue());
        assertTrue(e.evaluate(frame(104,20,20,List.of()),null).isEmpty());
        assertEquals(0,e.evaluate(frame(120,6,20,List.of()),null).getFirst().sinceS().intValue());
        e.reset();
        assertEquals(0,e.evaluate(frame(200,6,20,List.of()),null).getFirst().sinceS().intValue());
    }
    @Test public void activityAllKindsAndPriorityAndWindowExpiry() {
        Map<String,List<Action>> cases=Map.of("fighting",List.of(action(ActionType.ATTACK,"minecraft:zombie",1,true,true)),
        "looting",List.of(action(ActionType.OPEN,"minecraft:chest",2,true,true)),
        "mining",List.of(action(ActionType.BREAK,"minecraft:stone",15,true,true)),
        "building",List.of(action(ActionType.PLACE,"minecraft:stone",10,true,true)),
        "farming",List.of(new Action(ActionType.ANIMAL,"minecraft:cow",1,0,null,null,true,true,false,true,null,null)));
        for(var entry:cases.entrySet()) {
            ActivityInference a=new ActivityInference();
            a.observe(frame(0,20,20,List.of()));
            a.observe(frame(1,20,20,entry.getValue()));
            assertEquals(entry.getKey(),a.current().kind());
            a.observe(frame(1201,20,20,List.of()));
            assertEquals("idle",a.current().kind());
        }
        for(String screen:List.of("enchanting","crafting","furnace","smithing")) {
            ActivityInference a=new ActivityInference();
            a.observe(withClient(frame(0,20,20,List.of()),new Client(screen,null,null,12)));
            assertEquals(screen.equals("enchanting")?"enchanting":"crafting",a.current().kind());
        }
        ActivityInference afk=new ActivityInference();
        afk.observe(withClient(frame(0,20,20,List.of()),new Client(null,1801L,null,12)));
        assertEquals("afk",afk.current().kind());
        ActivityInference fish=new ActivityInference();
        var f=frame(0,20,20,List.of());
        fish.observe(new Observation(0,f.playerId(),f.dimension(),f.pos(),f.vital(),null,new Inventory(1,"minecraft:fishing_rod",50,100,true,true,
        true,null,List.of()),new Client(null,null,0L,12),List.of(),List.of(),List.of(),List.of(),false,false));
        assertEquals("fishing",fish.current().kind());
        for(double distance:List.of(7.99,8d,60d,120d)) {
            ActivityInference a=new ActivityInference();
            a.observe(frame(0,20,20,List.of()));
            var p=frame(1,20,20,List.of());
            a.observe(new Observation(1,p.playerId(),p.dimension(),List.of(distance,64d,0d),p.vital(),new Environment("minecraft:cherry_grove",null,
            null,null,null,null,0,1,false,false,"world",null,null,null),null,null,List.of(),List.of(),List.of(),List.of(),false,false));
            assertEquals(distance<8?"idle":distance==60?"exploring":"traveling",a.current().kind());
        }
    }
    private static Observation withClient(Observation o,Client c) {
        return new Observation(o.tick(),o.playerId(),o.dimension(),o.pos(),o.vital(),o.env(),o.inv(),c,o.actions(),o.mobs(),o.bosses(),o.structures(),
        false,false);
    }
}

package com.example.info2ai.capture.server;
import com.example.info2ai.model.*;
import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.*;
/** Pure, bounded per-entry history. No persistence, gameplay decisions, or input inference. */
public final class SessionHistory {
    private record Transition(String to,int day,int tick) { }
    private final Deque<Transition> transitions=new ArrayDeque<>();
    private final Deque<List<Double>> positions=new ArrayDeque<>();
    private String dimension;
    private int lastPositionTick,events,deaths,deathTick;
    private Facts death;
    public void observe(int tick,String dimension,List<Double> pos,int day) {
        if(this.dimension!=null && !this.dimension.equals(dimension)) {
            transitions.addFirst(new Transition(dimension,day,tick));events++;
            while(transitions.size()>100)transitions.removeLast();
        }
        this.dimension=dimension;
        if(positions.isEmpty() || tick-lastPositionTick>=200) {
            positions.addFirst(JsonUtil.immutableSlots(pos));lastPositionTick=tick;
            while(positions.size()>30)positions.removeLast();
        }
    }
    public void died(int tick,String cause,int x,int z,int day) {
        deaths++;events++;deathTick=tick;
        JsonObject o=new JsonObject();o.addProperty("cause",cause);o.addProperty("x",x);o.addProperty("z",z);o.addProperty("day",day);
        death=Facts.fromJson(o);
    }
    public HistoryInfo snapshot(int tick) {
        List<Facts> switches=transitions.stream().map(t->{
            JsonObject o=new JsonObject();o.addProperty("to",t.to());o.addProperty("day",t.day());o.addProperty("ago_s",Math.max(0,(tick-t.tick())/20));return Facts.fromJson(o);
        }).toList();
        JsonObject last=death==null?null:death.toJson();if(last!=null)last.addProperty("ago_s",Math.max(0,(tick-deathTick)/20));
        return new HistoryInfo(events,deaths,Facts.fromJson(last),switches,new ArrayList<>(positions),null,null,null,null);
    }
}

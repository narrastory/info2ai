package com.example.info2ai.events;
import java.util.*;
import com.example.info2ai.model.Facts;
/** 不持有 Snapshot；只保留一次轻量观察、常数空间冷却及各领域计数。 */
public final class EventEngine {
    private Observation previous;
    private List<EventDetector> detectors;
    private MiningDetector mining;
    private static final Set<String> MINING_FACTS=Set.of("mining_streak","mining_drought","block_broken_rare","ore_found");
    public static boolean interruptsMining(EventDraft event) {
        return !MINING_FACTS.contains(event.kind());
    }
    private final Map<String,Long> lastEmitMs=new HashMap<>();
    private final Map<String,Integer> suppressed=new HashMap<>();
    public EventEngine() {
        reset();
    }
    public List<EventDraft> tick(Observation o,long nowMs) {
        if(previous!=null && (!Objects.equals(previous.playerId(),o.playerId()) || o.tick()<previous.tick())) reset();
        if(previous!=null && o.tick()==previous.tick())return List.of();
        List<EventDraft> output=new ArrayList<>();
        EventContext ctx=new EventContext(previous,o,event-> {
            Long last=lastEmitMs.get(event.kind());
            if(last!=null && nowMs-last<EventCooldowns.VALUES.getOrDefault(event.kind(),0L)) {
                suppressed.merge(event.kind(),1,Integer::sum);
                return;
            }
            var data=event.data().toJson();
            data.addProperty("count",1+suppressed.getOrDefault(event.kind(),0));
            suppressed.remove(event.kind());
            output.add(new EventDraft(event.tick(),event.kind(),event.severity(),event.text(),Facts.fromJson(data),event.pos(),event.durationMs()));
            lastEmitMs.put(event.kind(),nowMs);
        } );
        for(EventDetector detector:detectors)detector.tick(ctx);
        if(output.stream().anyMatch(EventEngine::interruptsMining))mining.interrupt();
        previous=o;
        return List.copyOf(output);
    }
    public void reset() {
        previous=null;
        lastEmitMs.clear();
        suppressed.clear();
        mining=new MiningDetector();
        detectors=List.of(new SurvivalDetector(),new ExplorationDetector(),new CombatDetector(),mining,new ProgressDetector(),
        new WeatherDetector(),new SessionDetector());
    }
}

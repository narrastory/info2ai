package com.example.info2ai.events;
import com.example.info2ai.model.Facts;
import java.util.function.Consumer;
import static com.example.info2ai.events.EventData.of;
public record EventContext(Observation previous,Observation current,Consumer<EventDraft> sink) {
    public void emit(String kind,String severity,String text,Object... data) {
        emitAt(kind,severity,text,0,current.pos(),of(data));
    }
    public void emitAt(String kind,String severity,String text,long duration,java.util.List<Double> pos,Facts data) {
        var enriched=data.toJson();
        enriched.addProperty("dimension",current.dimension());
        sink.accept(new EventDraft(current.tick(),kind,severity,text,Facts.fromJson(enriched),pos,duration));
    }
    public void action(String kind,String severity,String text,Observation.Action a,Object... data) {
        var enriched=of(data).toJson();
        if(a.details()!=null)enriched.add("facts",a.details().toJson());
        var sourceDimension=a.details()==null?null:com.example.info2ai.util.JsonUtil.string(a.details().toJson(),"dimension");
        if(sourceDimension!=null)enriched.addProperty("source_dimension",sourceDimension);
        emitAt(kind,severity,text,0,a.pos()==null?current.pos():a.pos(),Facts.fromJson(enriched));
    }
}

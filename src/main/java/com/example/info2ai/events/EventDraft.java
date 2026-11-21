package com.example.info2ai.events;
import com.example.info2ai.model.Facts;
import java.util.List;
/** 尚未分配全局序号的不可变事件。 */
public record EventDraft(long tick,String kind,String severity,String text,Facts data,List<Double> pos,long durationMs) {
    public EventDraft {
        if(pos!=null)pos=List.copyOf(pos);
    }
}

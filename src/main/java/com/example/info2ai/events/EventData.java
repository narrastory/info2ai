package com.example.info2ai.events;
import com.example.info2ai.model.Facts;
import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
/** 事件和信号的数值事实构建器，不引用游戏类型。 */
public final class EventData {
    private EventData() {
    }
    public static Facts of(Object... pairs) {
        JsonObject json=new JsonObject();
        for(int i=0;i<pairs.length;i+=2) json.add((String)pairs[i],JsonUtil.encode(pairs[i+1],com.example.info2ai.model.Detail.FULL));
        return Facts.fromJson(json);
    }
}

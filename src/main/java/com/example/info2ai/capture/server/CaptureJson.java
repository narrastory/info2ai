package com.example.info2ai.capture.server;
import com.example.info2ai.util.JsonUtil;
import com.example.info2ai.model.*;
import com.google.gson.*;
import net.minecraft.util.math.Vec3d;
import java.util.*;
final class CaptureJson {
    private CaptureJson() { }
    static JsonObject object(Object... pairs) {
        JsonObject o=new JsonObject();
        for(int i=0;i<pairs.length;i+=2) JsonUtil.putNullable(o,(String)pairs[i],pairs[i+1]);
        return o;
    }
    static List<Double> vector(Vec3d v) { return List.of(v.x,v.y,v.z); }
    static Facts facts(Object... pairs) { return Facts.fromJson(object(pairs)); }
}

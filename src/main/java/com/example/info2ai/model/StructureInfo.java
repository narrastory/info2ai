package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record StructureInfo(
    String type,
    String structureId,
    Integer x,
    Integer z,
    Integer y,
    Double distance,
    String direction,
    Double bearing,
    String confidence,
    String note,
    Boolean visited,
    String variantStatus
) implements JsonModel {
    public StructureInfo(String type,String structureId,Integer x,Integer z,Integer y,Double distance,String direction,
            Double bearing,String confidence,String note,Boolean visited) {
        this(type,structureId,x,z,y,distance,direction,bearing,confidence,note,visited,null);
    }
    public static StructureInfo fromJson(JsonObject o) {
        if (o == null) return null;
        return new StructureInfo(
            JsonUtil.string(o, "type"),
            JsonUtil.string(o, "structure_id"),
            JsonUtil.integer(o, "x"),
            JsonUtil.integer(o, "z"),
            JsonUtil.integer(o, "y"),
            JsonUtil.number(o, "distance"),
            JsonUtil.string(o, "direction"),
            JsonUtil.number(o, "bearing"),
            JsonUtil.string(o, "confidence"),
            JsonUtil.string(o, "note"),
            JsonUtil.bool(o, "visited"),
            JsonUtil.string(o,"variant_status")
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "type", type, detail);
        JsonUtil.put(o, "structure_id", structureId, detail);
        if(variantStatus!=null)JsonUtil.put(o,"variant_status",variantStatus,detail);
        JsonUtil.put(o, "x", x, detail);
        JsonUtil.put(o, "z", z, detail);
        JsonUtil.put(o, "y", y, detail);
        JsonUtil.put(o, "distance", distance == null ? null : JsonUtil.round1(distance), detail);
        JsonUtil.put(o, "direction", direction, detail);
        JsonUtil.put(o, "bearing", bearing == null ? null : JsonUtil.round1(bearing), detail);
        JsonUtil.put(o, "confidence", confidence, detail);
        JsonUtil.put(o, "note", note, detail);
        JsonUtil.put(o, "visited", visited, detail);
        return o;
    }
}

package com.example.info2ai.staticworld;
import com.example.info2ai.model.*;
import com.example.info2ai.util.*;
import java.util.*;
import java.util.function.Predicate;

public final class StaticProjection {
    private StaticProjection() { }
    public static java.util.List<com.example.info2ai.events.Observation.Structure> observations(StaticWorldInfo info,String dimension,double x,double z) {
        if(info==null||info.structures()==null)return List.of();
        return info.structures().stream().map(item->new com.example.info2ai.events.Observation.Structure(key(dimension,item),item.structureId(),
            VecUtil.horizontalDistance(x,z,item.x(),item.z()),VecUtil.directionFromDelta(item.x()-x,item.z()-z),item.x(),item.z(),
            info.source(),item.confidence(),item.visited(),item.type(),item.variantStatus())).toList();
    }
    public static String key(String dimension,StructureInfo structure) {
        return dimension+"|"+structure.type()+"|"+structure.x()+"|"+structure.z();
    }
    public static StaticWorldInfo project(StaticWorldInfo absolute,String dimension,double x,double z,int radius,Predicate<String> visited) {
        if(absolute==null)return null;
        List<StructureInfo> relative=new ArrayList<>();
        for(var item:absolute.structures()) {
            double distance=VecUtil.horizontalDistance(x,z,item.x(),item.z());if(distance>radius)continue;
            relative.add(new StructureInfo(item.type(),item.structureId(),item.x(),item.z(),item.y(),distance,
                VecUtil.directionFromDelta(item.x()-x,item.z()-z),VecUtil.bearing(item.x()-x,item.z()-z),
                item.confidence(),item.note(),visited.test(key(dimension,item))?Boolean.TRUE:null,item.variantStatus()));
        }
        relative.sort(Comparator.comparingDouble(StructureInfo::distance).thenComparing(StructureInfo::type)
                .thenComparing(StructureInfo::x).thenComparing(StructureInfo::z));
        if(relative.size()>8)relative=new ArrayList<>(relative.subList(0,8));
        var coverage=absolute.coverage()==null?new com.google.gson.JsonObject():absolute.coverage().toJson();
        coverage.addProperty("display_radius",radius);coverage.addProperty("display_limit",8);
        coverage.addProperty("relative_origin_x",x);coverage.addProperty("relative_origin_z",z);
        coverage.addProperty("absence_proven",false);
        return new StaticWorldInfo(absolute.source(),absolute.confidence(),absolute.biome(),absolute.biomeScaleNote(),relative,
                absolute.nearestBiomes(),absolute.query(),Facts.fromJson(coverage),absolute.sampledAtMs());
    }
}

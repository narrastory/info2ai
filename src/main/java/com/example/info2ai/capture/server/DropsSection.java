package com.example.info2ai.capture.server;
import com.example.info2ai.model.Facts;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import java.util.*;
import static com.example.info2ai.capture.server.CaptureJson.*;
public final class DropsSection {
    public List<Facts> collect(ServerPlayerEntity p) {
        var items=p.getServerWorld().getEntitiesByClass(ItemEntity.class,new Box(p.getPos(),p.getPos()).expand(8),e->!e.getStack().isEmpty());
        items.sort(Comparator.comparingDouble(p::squaredDistanceTo));
        return items.stream().limit(16).map(e->facts("id",Registries.ITEM.getId(e.getStack().getItem()).toString(),"count",e.getStack().getCount(),
            "distance",Math.round(p.distanceTo(e)*10)/10.0)).toList();
    }
}

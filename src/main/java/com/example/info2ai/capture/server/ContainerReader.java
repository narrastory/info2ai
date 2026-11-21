package com.example.info2ai.capture.server;
import com.example.info2ai.model.ItemStackInfo;
import java.util.*;
import java.util.function.IntFunction;
/** The loot guard precedes every inventory getter, including isEmpty. */
public final class ContainerReader {
    private ContainerReader() { }
    public record Contents(boolean known,List<ItemStackInfo> items) { public Contents { items=List.copyOf(items); } }
    public static Contents read(String pendingLoot,int slots,IntFunction<ItemStackInfo> getter) {
        if(pendingLoot!=null)return new Contents(false,List.of());
        List<ItemStackInfo> items=new ArrayList<>();
        for(int i=0;i<slots;i++) { var item=getter.apply(i);if(item!=null)items.add(item); }
        return new Contents(true,items);
    }
}

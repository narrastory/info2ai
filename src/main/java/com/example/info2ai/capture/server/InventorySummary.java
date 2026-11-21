package com.example.info2ai.capture.server;
import com.example.info2ai.util.NameZh;
import com.example.info2ai.model.ItemStackInfo;
import java.util.*;
import java.util.stream.Collectors;
/** Pure aggregation, independent of client language and Minecraft runtime. */
public final class InventorySummary {
    public record Entry(String id,int count,int rarity) { }
    private InventorySummary() { }
    public static int used(List<ItemStackInfo> main) { return (int)main.stream().filter(Objects::nonNull).count(); }
    public static String summary(List<Entry> entries) {
        Map<String,Integer> counts=new HashMap<>(),rarities=new HashMap<>();
        entries.forEach(e->{counts.merge(e.id(),e.count(),Integer::sum);rarities.merge(e.id(),e.rarity(),Math::max);});
        return counts.keySet().stream().sorted(Comparator.<String>comparingLong(id->-(long)counts.get(id)*(1+rarities.get(id))).thenComparing(Comparator.naturalOrder()))
            .limit(5).map(id->counts.get(id)+" "+NameZh.get(id)).collect(Collectors.joining("、"));
    }
}

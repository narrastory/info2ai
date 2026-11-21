package com.example.info2ai.events;
import java.util.*;
import static com.example.info2ai.events.Observation.*;
import com.example.info2ai.util.NameZh;
public final class MiningDetector implements EventDetector {
    public static final Set<String> RARE=Set.of("diamond_ore","deepslate_diamond_ore","ancient_debris","emerald_ore","deepslate_emerald_ore",
    "lapis_ore","deepslate_lapis_ore","gold_ore","deepslate_gold_ore","nether_gold_ore","amethyst_cluster","spawner","dragon_egg","beacon",
    "budding_amethyst","reinforced_deepslate","sculk_catalyst","sculk_shrieker");
    private int broken,stones;
    private long started,lastBreak;
    public void interrupt() {
        broken=0;
        stones=0;
    }
    public String kind() {
        return "mining";
    }
    public static boolean ore(String id) {
        return id!=null&&(id.endsWith("_ore")||id.equals("minecraft:ancient_debris"));
    }
    public static boolean significant(String id) {
        return id!=null&&(id.endsWith("_bed")||Set.of("minecraft:crafting_table","minecraft:chest","minecraft:trapped_chest","minecraft:ender_chest",
        "minecraft:furnace","minecraft:blast_furnace","minecraft:smoker","minecraft:enchanting_table").contains(id));
    }
    public void tick(EventContext c) {
        var n=c.current();
        var p=c.previous();
        if(broken>0&&n.tick()-lastBreak>=ActivityInference.MINING_IDLE_TIMEOUT_TICKS)interrupt();
        for(var a:n.actions())if(a.confirmed()) {
            if(a.type()==ActionType.BREAK) {
                if(broken==0)started=n.tick();
                lastBreak=n.tick();
                broken+=a.count();
                if(ore(a.id()))stones=0;
                else if(a.stone())stones+=a.count();
                else stones=0;
                if(RARE.contains(a.id().replace("minecraft:","")))c.action("block_broken_rare","info","挖到了"+NameZh.get(a.id()),a,"id",a.id(),"blocks",
                a.count());
                if(ore(a.id()))c.action("ore_found","info","挖到"+NameZh.get(a.id()),a,"ore",a.id(),"blocks",a.count());
                if(stones>=120)c.emitAt("mining_drought","info","连续挖了 "+stones+" 个石头，期间没有挖到矿石",(n.tick()-started)*50,n.pos(),EventData.of("n",stones));
                if(broken>=200)c.emitAt("mining_streak","info","连续破坏了 "+broken+" 个方块",(n.tick()-started)*50,n.pos(),EventData.of("n",broken,"minutes",
                (n.tick()-started)/1200d));
            } else if(a.type()==ActionType.TOOL_BREAK)c.action("tool_broke","warn","手持工具耐久耗尽",a,"tool",a.id());
            else if(a.type()==ActionType.PLACE) {
                if(significant(a.id()))c.action("placed_significant","info","放置了"+NameZh.get(a.id()),a,"block",a.id());
            }
            else if(a.type()==ActionType.OPEN) {
                c.action("chest_opened","info","打开了"+NameZh.get(a.id()),a,"container",a.id());
                if(n.inv()!=null&&!n.inv().rareLoot().isEmpty())c.action("chest_loot_found","info","打开的容器里有稀有物品",a,"items",n.inv().rareLoot());
            } else if(a.type()==ActionType.DROP && n.inv()!=null&&n.inv().free()==0)c.action("inventory_full_lost","warn","背包已满，观察到挖矿掉落物留在地面",a,
            "item",a.id(),"items",a.count());
            else if(a.type()==ActionType.ENCHANT)c.action("enchant_done","info","物品完成附魔",a,"item",a.id(),"ench",a.details());
            if(a.type()!=ActionType.BREAK && a.type()!=ActionType.TOOL_BREAK) {
                broken=0;
                stones=0;
            }
        }
        if(p!=null && n.inv()!=null && p.inv()!=null) {
            var a=n.inv();
            var b=p.inv();
            if(a.free()==0&&b.free()!=0)c.emit("inventory_full","warn","背包满了","free",0);
            if(a.maxDurability()>0&&a.durability()/(double)a.maxDurability()<.15 && (b.maxDurability()==0||!Objects.equals(a.heldId(),
            b.heldId())||b.durability()/(double)b.maxDurability()>=.15))c.emit("tool_low","warn",NameZh.get(a.heldId())+" 耐久剩 "+a.durability(),
            "tool",a.heldId(),"remaining",a.durability(),"max",a.maxDurability());
        }
        if(p!=null&&n.vital()!=null&&p.vital()!=null&&!Objects.equals(n.vital().equipment(),p.vital().equipment()))c.emit("equipment_changed","info",
        "装备发生变化","equipment",n.vital().equipment());
        if(p!=null&&n.vital()!=null&&p.vital()!=null&&(n.vital().health()<p.vital().health()||!Objects.equals(n.dimension(),p.dimension()))) {
            broken=0;
            stones=0;
        }
    }
}

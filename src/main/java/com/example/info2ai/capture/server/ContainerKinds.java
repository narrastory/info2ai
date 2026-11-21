package com.example.info2ai.capture.server;

import net.minecraft.screen.*;
import java.util.Map;

/** 只识别真实已打开的原版持久物品容器，临时工作站/交易界面不当储物容器，不读取其中任何库存或loot。 */
public final class ContainerKinds {
    private static final Map<Class<?>,String> STORAGE_HANDLERS=Map.ofEntries(
        Map.entry(FurnaceScreenHandler.class,"furnace"),Map.entry(BlastFurnaceScreenHandler.class,"blast_furnace"),
        Map.entry(SmokerScreenHandler.class,"smoker"),Map.entry(BrewingStandScreenHandler.class,"brewing_stand"),
        Map.entry(Generic3x3ContainerScreenHandler.class,"generic_3x3"),Map.entry(CrafterScreenHandler.class,"crafter"),
        Map.entry(HorseScreenHandler.class,"horse"),Map.entry(LecternScreenHandler.class,"lectern"));
    private ContainerKinds() { }
    public static String kind(Class<?> type) {
        if(type==null)return null;
        if(GenericContainerScreenHandler.class.isAssignableFrom(type))return "chest";
        if(ShulkerBoxScreenHandler.class.isAssignableFrom(type))return "shulker_box";
        if(HopperScreenHandler.class.isAssignableFrom(type))return "hopper";
        String exact=STORAGE_HANDLERS.get(type);
        if(exact!=null)return exact;
        for(var entry:STORAGE_HANDLERS.entrySet())if(entry.getKey().isAssignableFrom(type))return entry.getValue();
        if(AbstractFurnaceScreenHandler.class.isAssignableFrom(type))return "furnace";
        return null;
    }
    public static String opened(Class<?> previous,Class<?> current,boolean changed) {
        return previous!=null&&changed?kind(current):null;
    }
}

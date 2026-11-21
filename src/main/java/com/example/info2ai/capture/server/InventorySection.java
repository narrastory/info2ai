package com.example.info2ai.capture.server;
import com.example.info2ai.model.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.tag.ItemTags;
import java.util.*;
import static com.example.info2ai.capture.server.CaptureJson.*;
public final class InventorySection {
    public InventoryInfo collect(ServerPlayerEntity p) {
        var inv=p.getInventory();
        List<ItemStackInfo> main=new ArrayList<>(),armor=new ArrayList<>();
        for(int i=0;i<36;i++)main.add(ItemCapture.read(inv.getStack(i),i));
        for(int i=39;i>=36;i--)armor.add(ItemCapture.read(inv.getStack(i),i));
        List<ItemStack> stacks=new ArrayList<>();
        for(int i=0;i<36;i++)stacks.add(inv.getStack(i));
        for(int i=36;i<=40;i++)stacks.add(inv.getStack(i));
        int food=0,broken=0,low=0,torches=0,arrows=0,pearls=0,buckets=0,empty=0,blocks=0;
        boolean light=false,bed=false;
        List<Facts> tools=new ArrayList<>();List<String> valuable=new ArrayList<>();List<InventorySummary.Entry> summary=new ArrayList<>();
        for(var stack:stacks) {
            if(stack.isEmpty())continue;
            var item=stack.getItem();String id=Registries.ITEM.getId(item).toString();int n=stack.getCount();
            summary.add(new InventorySummary.Entry(id,n,stack.getRarity().ordinal()));
            var nutrition=stack.get(DataComponentTypes.FOOD);if(nutrition!=null)food+=nutrition.nutrition()*n;
            if(stack.isDamageable() && !(item instanceof ArmorItem) && item!=Items.ELYTRA) {
                int remaining=stack.getMaxDamage()-stack.getDamage();
                tools.add(facts("id",id,"durability",remaining,"durability_max",stack.getMaxDamage()));
                if(remaining<=0)broken++;
                if(remaining<stack.getMaxDamage()*.15)low++;
            }
            if(item==Items.TORCH || item==Items.SOUL_TORCH)torches+=n;
            if(stack.isIn(ItemTags.ARROWS))arrows+=n;
            if(item==Items.ENDER_PEARL)pearls+=n;
            if(item instanceof BucketItem || item==Items.MILK_BUCKET)buckets+=n;
            if(item==Items.BUCKET)empty+=n;
            if(item instanceof BlockItem block) { blocks+=n; if(block.getBlock().getDefaultState().getLuminance()>0)light=true; }
            if(stack.isIn(ItemTags.BEDS))bed=true;
            if(Set.of("minecraft:diamond","minecraft:emerald","minecraft:netherite_ingot","minecraft:ancient_debris","minecraft:nether_star","minecraft:enchanted_golden_apple").contains(id) && !valuable.contains(id))valuable.add(id);
        }
        var aggregate=facts("food_points",food,"has_food",food>0,"tools",tools,"broken_tools",broken,"low_tools",low,"torches",torches,"arrows",arrows,
            "ender_pearls",pearls,"buckets",buckets,"empty_buckets",empty,"blocks_placeable",blocks,"has_light_source",light,"bed",bed,"valuable",valuable);
        List<ItemStackInfo> ender=new ArrayList<>(); int enderUsed=0;
        for(int i=0;i<27;i++) { var item=ItemCapture.read(p.getEnderChestInventory().getStack(i),i);ender.add(item);if(item!=null)enderUsed++; }
        int used=InventorySummary.used(main);
        return new InventoryInfo(used,36-used,main,main.subList(0,9),armor,ItemCapture.read(inv.getStack(40),40),facts("used",enderUsed,"items",ender),null,aggregate,InventorySummary.summary(summary));
    }
}

package com.example.info2ai.capture.server;
import com.example.info2ai.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import java.util.*;
public final class ItemCapture {
    private ItemCapture() { }
    public static ItemStackInfo read(ItemStack stack, Integer slot) {
        if(stack.isEmpty()) return null;
        List<Facts> enchantments=new ArrayList<>();
        for(var e:EnchantmentHelper.getEnchantments(stack).getEnchantmentEntries()) {
            enchantments.add(CaptureJson.facts("id",e.getKey().getKey().map(k->k.getValue().toString()).orElse(null),"level",e.getIntValue()));
        }
        var custom=stack.get(DataComponentTypes.CUSTOM_NAME);
        return new ItemStackInfo(Registries.ITEM.getId(stack.getItem()).toString(),stack.getCount(),
            stack.isDamageable()?stack.getDamage():null,stack.getMaxDamage(),custom==null?null:custom.getString(),enchantments,null,slot,
            com.example.info2ai.director.DirectorEvents.action(stack));
    }
}

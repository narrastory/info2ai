package com.example.info2ai.mixin;
import net.minecraft.client.toast.AdvancementToast;
import net.minecraft.advancement.AdvancementEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AdvancementToast.class)
public interface AdvancementToastAccessor {
    @Accessor("advancement") AdvancementEntry info2ai$advancement();
}

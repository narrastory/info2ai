package com.example.info2ai.mixin;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(SystemToast.class)
public interface SystemToastAccessor {
    @Accessor("title") Text info2ai$title();
}

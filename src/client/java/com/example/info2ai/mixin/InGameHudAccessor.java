package com.example.info2ai.mixin;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(InGameHud.class)
public interface InGameHudAccessor {
    @Accessor("title") Text info2ai$title();
    @Accessor("subtitle") Text info2ai$subtitle();
    @Accessor("overlayMessage") Text info2ai$overlay();
    @Accessor("titleRemainTicks") int info2ai$titleTicks();
    @Accessor("overlayRemaining") int info2ai$overlayTicks();
}

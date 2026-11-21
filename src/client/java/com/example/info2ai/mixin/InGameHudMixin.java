package com.example.info2ai.mixin;
import com.example.info2ai.capture.client.ClientObservations;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method="setTitle",at=@At("HEAD"),require=0)
    private void info2ai$title(Text text,CallbackInfo ci) { info2ai$record("title",text); }
    @Inject(method="setSubtitle",at=@At("HEAD"),require=0)
    private void info2ai$subtitle(Text text,CallbackInfo ci) { info2ai$record("subtitle",text); }
    @Inject(method="setOverlayMessage",at=@At("HEAD"),require=0)
    private void info2ai$overlay(Text text,boolean tinted,CallbackInfo ci) { info2ai$record("actionbar",text); }
    private static void info2ai$record(String kind,Text text) {
        var owner=ClientObservations.context();if(owner==null)return;
        try { ClientObservations.hud(owner,kind,text==null?null:text.getString()); }
        catch (RuntimeException | LinkageError failure) { ClientObservations.failed(owner,"capture_hud_"+kind+"_hook_failed",failure); }
    }
}

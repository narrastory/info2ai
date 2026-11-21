package com.example.info2ai.mixin;
import com.example.info2ai.capture.client.ClientObservations;
import net.minecraft.client.toast.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method="add",at=@At("HEAD"),require=0)
    private void info2ai$toast(Toast toast,CallbackInfo ci) {
        var owner=ClientObservations.context();if(owner==null)return;
        try {
            String type="other", text=null;
            if (toast instanceof AdvancementToastAccessor access) {
                type="advancement";
                text=access.info2ai$advancement().value().display().map(display->display.getTitle().getString()).orElse(null);
            } else if (toast instanceof SystemToastAccessor access) {
                type="system"; text=access.info2ai$title().getString();
            } else if (toast instanceof RecipeToast) type="recipe";
            ClientObservations.toast(owner,type,text);
        } catch (RuntimeException | LinkageError failure) { ClientObservations.failed(owner,"capture_toast_hook_failed",failure); }
    }
}

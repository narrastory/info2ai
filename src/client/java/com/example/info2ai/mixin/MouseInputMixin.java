package com.example.info2ai.mixin;
import com.example.info2ai.capture.client.ClientObservations;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Mouse.class)
public abstract class MouseInputMixin {
    @Inject(method="onMouseButton",at=@At("HEAD"),require=0)
    private void info2ai$button(long window,int button,int action,int mods,CallbackInfo ci) {
        ClientObservations.input(2,window);
    }
    @Inject(method="onMouseScroll",at=@At("HEAD"),require=0)
    private void info2ai$scroll(long window,double horizontal,double vertical,CallbackInfo ci) {
        ClientObservations.input(3,window);
    }
    @Inject(method="onCursorPos",at=@At("HEAD"),require=0)
    private void info2ai$cursor(long window,double x,double y,CallbackInfo ci) {
        ClientObservations.input(4,window);
    }
}

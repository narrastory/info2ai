package com.example.info2ai.mixin;
import com.example.info2ai.capture.client.ClientObservations;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Keyboard.class)
public abstract class KeyboardInputMixin {
    @Inject(method="onKey",at=@At("HEAD"),require=0)
    private void info2ai$key(long window,int key,int scan,int action,int mods,CallbackInfo ci) {
        ClientObservations.input(0,window);
    }
    @Inject(method="onChar",at=@At("HEAD"),require=0)
    private void info2ai$char(long window,int code,int mods,CallbackInfo ci) {
        ClientObservations.input(1,window);
    }
}

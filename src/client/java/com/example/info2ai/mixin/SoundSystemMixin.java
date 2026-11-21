package com.example.info2ai.mixin;
import com.example.info2ai.capture.client.ClientObservations;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @Inject(method="play(Lnet/minecraft/client/sound/SoundInstance;)V",at=@At("HEAD"),require=0)
    private void info2ai$sound(SoundInstance sound,CallbackInfo ci) {
        var owner=ClientObservations.context();if(owner==null)return;
        try {
            ClientObservations.sound(owner,sound.getId().toString(),sound.getCategory()==SoundCategory.MUSIC,
                sound.isRelative() || sound.getAttenuationType()==SoundInstance.AttenuationType.NONE,
                sound.getX(),sound.getY(),sound.getZ(),System.currentTimeMillis());
        } catch (RuntimeException | LinkageError failure) { ClientObservations.failed(owner,"capture_sound_hook_failed",failure); }
    }
}

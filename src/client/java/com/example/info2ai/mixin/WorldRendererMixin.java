package com.example.info2ai.mixin;
import com.example.info2ai.capture.client.ClientObservations;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method="renderEntity",at=@At("HEAD"),require=0)
    private void info2ai$rendered(Entity entity,double x,double y,double z,float delta,MatrixStack matrices,VertexConsumerProvider consumers,CallbackInfo ci) {
        var owner=ClientObservations.context();if(owner==null)return;
        try { ClientObservations.rendered(owner,entity.getId()); }
        catch (RuntimeException | LinkageError failure) { ClientObservations.failed(owner,"capture_entity_render_hook_failed",failure); }
    }
}

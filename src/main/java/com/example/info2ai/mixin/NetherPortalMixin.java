package com.example.info2ai.mixin;
import com.example.info2ai.capture.server.ServerCapture;
import net.minecraft.world.dimension.NetherPortal;
import net.minecraft.world.WorldAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(NetherPortal.class)
public abstract class NetherPortalMixin {
    @Shadow @Final private WorldAccess world;
    @Shadow private BlockPos lowerCorner;
    @Inject(method="createPortal",at=@At("RETURN"),require=0)
    private void info2ai$portal(CallbackInfo ci) {
        if(world instanceof ServerWorld server)ServerCapture.portalObserved(server,lowerCorner);
    }
}

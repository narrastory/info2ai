package com.example.info2ai.mixin;

import com.example.info2ai.capture.server.TeleportSequence;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Optional observation of the counter incremented by every vanilla teleport/correction request. */
@Mixin(ServerPlayNetworkHandler.class)
public interface ServerPlayNetworkHandlerAccessor extends TeleportSequence {
    @Override
    @Accessor("requestedTeleportId")
    int info2ai$teleportSequence();
}

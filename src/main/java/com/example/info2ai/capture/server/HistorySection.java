package com.example.info2ai.capture.server;
import com.example.info2ai.model.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
public final class HistorySection {
    private final SessionHistory history=new SessionHistory();
    public void died(ServerPlayerEntity player,DamageSource source) {
        history.died(player.getServer().getTicks(),PlayerCoreSection.damageKind(source),player.getBlockX(),player.getBlockZ(),TimeInfo.fromTicks(player.getServerWorld().getTimeOfDay()).day());
    }
    public void observe(ServerPlayerEntity player) {
        history.observe(player.getServer().getTicks(),player.getServerWorld().getRegistryKey().getValue().toString(),CaptureJson.vector(player.getPos()),TimeInfo.fromTicks(player.getServerWorld().getTimeOfDay()).day());
    }
    public HistoryInfo collect(ServerPlayerEntity player) { observe(player);return history.snapshot(player.getServer().getTicks()); }
}

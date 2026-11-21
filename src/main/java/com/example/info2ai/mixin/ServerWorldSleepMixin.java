package com.example.info2ai.mixin;
import com.example.info2ai.capture.server.*;
import com.example.info2ai.events.Observation.ActionType;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 此私有方法的唯一原版调用点已满足 SleepManager 的跳夜条件。 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldSleepMixin {
    @Unique private long info2ai$beforeDayTime;
    @Inject(method="tick",at=@At("HEAD"),require=0)
    private void info2ai$beforeTick(java.util.function.BooleanSupplier supplier,CallbackInfo ci) {
        info2ai$beforeDayTime=((ServerWorld)(Object)this).getTimeOfDay();
    }
    @Inject(method="wakeSleepingPlayers",at=@At("HEAD"),require=0)
    private void info2ai$slept(CallbackInfo ci) {
        var world=(ServerWorld)(Object)this;
        if(!com.example.info2ai.events.ActionEvidence.slept(world.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE),
        info2ai$beforeDayTime,world.getTimeOfDay()))return;
        for(var p:world.getPlayers())if(p.isSleeping())ServerCapture.eventFact(p,()->ServerFactHooks.action(p,ActionType.SLEEP_COMPLETE,"sleep",1,0,
        null,com.example.info2ai.events.EventData.of("skipped_ticks",world.getTimeOfDay()-info2ai$beforeDayTime,"day_time",
        Math.floorMod(world.getTimeOfDay(),24000))));
    }
}

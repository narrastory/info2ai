package com.example.info2ai.mixin;
import com.example.info2ai.capture.server.*;
import com.example.info2ai.events.*;
import com.example.info2ai.events.Observation.ActionType;
import net.minecraft.advancement.*;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerAdvancementTracker.class)
public abstract class PlayerAdvancementTrackerMixin {
    @Shadow private ServerPlayerEntity owner;
    @Shadow public abstract AdvancementProgress getProgress(AdvancementEntry advancement);
    @Unique private final java.util.ArrayDeque<Boolean> info2ai$before=new java.util.ArrayDeque<>();
    @Inject(method="grantCriterion",at=@At("HEAD"),require=0)
    private void info2ai$before(AdvancementEntry advancement,String criterion,CallbackInfoReturnable<Boolean> result) {
        ServerCapture.eventFact(owner,()-> {
            info2ai$before.addLast(getProgress(advancement).isDone());
            return null;
        } );
    }
    @Inject(method="grantCriterion",at=@At("RETURN"),require=0)
    private void info2ai$after(AdvancementEntry advancement,String criterion,CallbackInfoReturnable<Boolean> result) {
        ServerCapture.eventFact(owner,()-> {
            Boolean wasDone=info2ai$before.pollLast();
            if(wasDone==null||wasDone||!result.getReturnValueZ()||!getProgress(advancement).isDone()||advancement.value().display().isEmpty())return null;
            return ServerFactHooks.action(owner,ActionType.ADVANCEMENT,advancement.id().toString(),1,0,null,EventData.of("name",
            advancement.value().display().get().getTitle().getString()));
        } );
    }
}

package com.example.info2ai.mixin.director;

import com.example.info2ai.director.guard.BlockJournal;
import net.minecraft.server.command.FillCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FillCommand.class)
abstract class DirectorFillMixin {
    @Redirect(method = "execute", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Clearable;clear(Ljava/lang/Object;)V"), require = 1)
    private static void info2ai$beforeClear(Object object) { BlockJournal.clear(object); }
}

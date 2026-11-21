package com.example.info2ai.mixin;
import net.minecraft.client.gui.hud.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.*;
@Mixin(BossBarHud.class)
public interface BossBarHudAccessor {
    @Accessor("bossBars") Map<UUID, ClientBossBar> info2ai$bossBars();
}

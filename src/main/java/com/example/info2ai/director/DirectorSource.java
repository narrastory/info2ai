package com.example.info2ai.director;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;

final class DirectorSource {
    private DirectorSource() { }

    static ServerCommandSource create(ServerPlayerEntity player, DirectorSettings settings, CapturingOutput output) {
        return new ServerCommandSource(output, player.getPos(), new Vec2f(0, player.getYaw()),
            player.getServerWorld(), settings.permissionLevel(), "info2ai", Text.literal(settings.speakerName()),
            player.getServer(), null);
    }
}

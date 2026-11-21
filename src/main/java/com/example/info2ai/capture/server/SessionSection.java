package com.example.info2ai.capture.server;

import com.example.info2ai.model.SessionInfo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.WorldSavePath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.util.*;

/** Per-world metadata is prepared once, never read from disk during a tick. */
public final class SessionSection {
    private final long started = System.nanoTime();
    private final String worldId;
    private final List<String> mods, ids;
    public SessionSection(MinecraftServer server) {
        worldId = worldId(server.getSavePath(WorldSavePath.ROOT));
        var installed = FabricLoader.getInstance().getAllMods().stream()
            .sorted(Comparator.comparing(m -> m.getMetadata().getId())).toList();
        ids = installed.stream().map(m -> m.getMetadata().getId()).toList();
        mods = installed.stream().limit(200).map(m -> m.getMetadata().getId()+"@"+m.getMetadata().getVersion().getFriendlyString()).toList();
    }
    public SessionInfo collect(MinecraftServer server, ServerPlayerEntity player) {
        var properties = server.getSaveProperties();
        return new SessionInfo(properties.getLevelName(), worldId, Long.toString(server.getOverworld().getSeed()),
            player.interactionManager.getGameMode().getName(), properties.getDifficulty().getName(), properties.isHardcore(),
            SharedConstants.getGameVersion().getName(), "fabric", ids.size(), mods, ids,
            (int)((System.nanoTime()-started)/1_000_000_000L), player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME))/20, null);
    }
    public static String worldId(Path path) {
        try {
            String normalized = path.toAbsolutePath().normalize().toString();
            if (System.getProperty("os.name", "").startsWith("Windows")) normalized = normalized.toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

package com.example.info2ai.director;

import com.example.info2ai.director.guard.EntityJournal;
import com.example.info2ai.director.log.MarkerStore;
import com.example.info2ai.events.EventData;
import com.example.info2ai.model.Facts;
import com.example.info2ai.util.JsonUtil;
import com.example.info2ai.util.VecUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

final class DirectorSection {
    private DirectorSection() { }

    private record Nearby(double distance, Supplier<Facts> facts) { }

    static Facts capture(DirectorService director, ServerPlayerEntity player, MarkerStore markers) {
        var status = director.status();
        var now = System.currentTimeMillis();
        var world = player.getServerWorld();
        var dimension = world.getRegistryKey().getValue().toString();
        var nearby = new ArrayList<Nearby>();
        int active = 0;
        for (var mark : markers.entities()) {
            var targetWorld = player.getServer().getWorld(net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.WORLD, net.minecraft.util.Identifier.of(mark.dimension())));
            var entity = targetWorld == null ? null : targetWorld.getEntity(mark.id());
            if (entity == null || !entity.isAlive() || !entity.getCommandTags().contains("info2ai")) continue;
            active++;
            if (targetWorld != world) continue;
            var delta = entity.getPos().subtract(player.getPos());
            double distance = delta.horizontalLength();
            var living = entity instanceof LivingEntity value ? value : null;
            nearby.add(new Nearby(distance, () -> EventData.of("action_id", mark.actionId(), "type", EntityJournal.type(entity),
                "name", entity.getName().getString(), "distance", JsonUtil.round1(distance), "direction", direction(delta),
                "health", living == null ? null : living.getHealth(), "max_health", living == null ? null : living.getMaxHealth(),
                "targeting_player", entity instanceof MobEntity mob && mob.getTarget() == player,
                "age_s", Math.max(0, (now - mark.createdAt()) / 1000))));
        }
        var containers = new ArrayList<Nearby>();
        for (var mark : markers.blocks()) {
            if (!mark.location().dimension().equals(dimension)) continue;
            var pos = mark.location().pos();
            var delta = Vec3d.ofCenter(pos).subtract(player.getPos());
            double distance = delta.horizontalLength();
            containers.add(new Nearby(distance, () -> EventData.of("action_id", mark.actionId(), "block", mark.block(),
                "pos", List.of(pos.getX(), pos.getY(), pos.getZ()), "distance", JsonUtil.round1(distance),
                "direction", direction(delta), "opened", mark.openedCount() > 0)));
        }
        var recent = director.actions(5).stream().map(action -> EventData.of("action_id", action.actionId(),
            "intent", action.intent(), "status", action.status().name().toLowerCase(Locale.ROOT),
            "ago_s", Math.max(0, (now - action.t()) / 1000), "undoable", action.undoable())).toList();
        return EventData.of("enabled", status.enabled(), "rate", EventData.of("limit_per_min", status.limits().perMinute(),
                "remaining", status.rate().remaining(), "reset_in_s", status.rate().resetInSeconds()),
            "active_entities", EventData.of("total", active, "cap", status.limits().activeEntities(), "nearest", closest(nearby)),
            "markers", closest(containers), "recent_actions", recent);
    }

    private static List<Facts> closest(List<Nearby> candidates) {
        return candidates.stream().sorted(Comparator.comparingDouble(Nearby::distance)).limit(8)
            .map(candidate -> candidate.facts().get()).toList();
    }

    private static String direction(Vec3d delta) { return VecUtil.directionFromDelta(delta.x, delta.z); }
}

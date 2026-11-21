package com.example.info2ai.director.guard;

import com.example.info2ai.api.DirectorRequests.SpawnedEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class EntityJournal {
    private final List<Entity> spawned = new ArrayList<>();
    private final Map<Entity, Position> moved = new IdentityHashMap<>();
    private final Map<Entity, Vec3d> pushed = new IdentityHashMap<>();

    record Position(Vec3d pos, float yaw, float pitch) { }

    public void spawn(Entity entity) { spawned.add(entity); }

    public void move(Entity entity) {
        if (ActionWindow.registered(entity) && !moved.containsKey(entity)) {
            moved.put(entity, new Position(entity.getPos(), entity.getYaw(), entity.getPitch()));
        }
    }

    public void push(Entity entity) {
        if (ActionWindow.registered(entity)) pushed.putIfAbsent(entity, entity.getVelocity());
    }

    public List<Entity> spawned() { return List.copyOf(spawned); }
    Map<Entity, Position> moved() { return moved; }
    Map<Entity, Vec3d> pushed() { return pushed; }

    public static boolean fakeDrop(Entity entity) {
        return entity instanceof ItemEntity item && item.getItemAge() >= 5999;
    }

    public static boolean counted(Entity entity) {
        return !(entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity);
    }

    public static String type(Entity entity) { return Registries.ENTITY_TYPE.getId(entity.getType()).toString(); }

    public List<SpawnedEntity> summary() {
        return spawned.stream().filter(entity -> !fakeDrop(entity)).map(entity ->
            new SpawnedEntity(entity.getUuidAsString(), type(entity), entity.getName().getString(),
                List.of(entity.getX(), entity.getY(), entity.getZ()))).toList();
    }
}

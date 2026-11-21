package com.example.info2ai.director.guard;

import com.example.info2ai.api.DirectorRequests.Veto;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/** 窗口仅对执行指令的服务端线程可见。 */
public final class ActionWindow {
    private static volatile ActionWindow current;
    private final Thread owner = Thread.currentThread();
    private final ServerPlayerEntity player;
    private final long actionId;
    private final BlockJournal blocks = new BlockJournal();
    private final EntityJournal entities = new EntityJournal();
    private final List<Veto> vetoes = new ArrayList<>();
    private final BlockPos feet;
    private final Box body;
    private final float maxHealth;
    private final List<StatusEffectInstance> effects;
    private final InventoryDiff inventory;

    private ActionWindow(long actionId, ServerPlayerEntity player) {
        this.actionId = actionId;
        this.player = player;
        feet = player.getBlockPos();
        body = player.getBoundingBox();
        maxHealth = player.getMaxHealth();
        effects = player.getStatusEffects().stream().map(StatusEffectInstance::new).toList();
        inventory = new InventoryDiff(player);
    }

    public static ActionWindow open(long actionId, ServerPlayerEntity player) {
        if (current != null) throw new IllegalStateException("导演指令窗口已打开");
        var window = new ActionWindow(actionId, player);
        current = window;
        return window;
    }

    public void close() { current = null; }

    public static ActionWindow active() {
        var window = current;
        return window != null && window.owner == Thread.currentThread() ? window : null;
    }

    static boolean registered(Entity entity) {
        return entity.getWorld() instanceof ServerWorld world && world.getEntity(entity.getUuid()) == entity;
    }

    public static boolean veto(Entity entity, String kind, Double amount) {
        var window = active();
        if (window == null || !(entity.getWorld() instanceof ServerWorld)) return false;
        if (window.vetoes.size() < 256) {
            window.vetoes.add(new Veto(kind, Registries.ENTITY_TYPE.getId(entity.getType()).toString(), amount));
        }
        return true;
    }

    public static boolean vetoHealth(LivingEntity entity, float health) {
        return active() != null && health < entity.getHealth() && registered(entity)
            && veto(entity, "health", (double) (entity.getHealth() - health));
    }

    public static boolean vetoRemoval(LivingEntity entity, Entity.RemovalReason reason) {
        return (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)
            && veto(entity, "remove", null);
    }

    public long actionId() { return actionId; }
    public ServerPlayerEntity player() { return player; }
    public BlockJournal blocks() { return blocks; }
    public EntityJournal entities() { return entities; }
    public InventoryDiff inventory() { return inventory; }
    public List<Veto> vetoes() { return List.copyOf(vetoes); }
    BlockPos feet() { return feet; }
    Box body() { return body; }
    float maxHealth() { return maxHealth; }
    List<StatusEffectInstance> effects() { return effects; }
}

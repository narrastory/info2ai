package com.example.info2ai.capture.server;
import com.example.info2ai.model.*;
import com.example.info2ai.util.VecUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.Registries;
import net.minecraft.block.Blocks;
import java.util.*;
import static com.example.info2ai.capture.server.CaptureJson.*;
public final class PlayerCoreSection {
    private Facts damage;
    private long damagedAt;
    private final MovementSpeed movement = new MovementSpeed();
    private boolean speedSourceWarned;

    public void resetMovement() {
        movement.reset();
    }
    public void clearDamage() {
        damage = null;
        damagedAt = 0;
    }

    public void damaged(ServerPlayerEntity p, DamageSource source, AppliedDamage applied) {
        damagedAt = p.getServer().getTicks();
        String attacker = source.getAttacker() == null ? null : Registries.ENTITY_TYPE.getId(source.getAttacker().getType()).toString();
        damage = facts("source", damageKind(source), "attacker", attacker, "amount", applied.amount(),
            "health_amount", applied.health(), "absorption_amount", applied.absorption());
    }

    public static String damageKind(DamageSource source) {
        Set<String> tags = new HashSet<>();
        if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)) tags.add("explosion");
        if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)) tags.add("fire");
        if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL)) tags.add("fall");
        if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_DROWNING)) tags.add("drowning");
        String type = source.getTypeRegistryEntry().getKey().map(key -> key.getValue().toString()).orElse("");
        String attacker = source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity ? "player"
            : source.getAttacker() instanceof net.minecraft.entity.mob.MobEntity ? "mob" : null;
        return DamageKind.classify(type, tags, attacker);
    }

    public PlayerInfo collect(ServerPlayerEntity p, DamageHook hook) {
        var v=p.getVelocity(); var hunger=p.getHungerManager();
        String dimension = p.getServerWorld().getRegistryKey().getValue().toString();
        Double speed = movement.observe(p.networkHandler, dimension, p.getId(), p.getServer().getTicks(), p.getX(), p.getZ());
        var store = com.example.info2ai.api.Info2AiApi.store();
        if (!movement.sourceAvailable()) {
            if (store != null) store.warning("capture_speed_source_unavailable");
            if (!speedSourceWarned) CaptureLog.error("传送序号来源不可用，speed_mps 保持未知", null);
            speedSourceWarned = true;
        } else {
            if (store != null) store.clearWarning("capture_speed_source_unavailable");
            speedSourceWarned = false;
        }
        List<Facts> effects=p.getStatusEffects().stream().map(e->facts("id",e.getEffectType().getKey().map(k->k.getValue().toString()).orElse(null),
            "amplifier",e.getAmplifier(),"duration_s",e.isInfinite()?-1:e.getDuration()/20,"ambient",e.isAmbient(),"visible",e.shouldShowParticles())).toList();
        Facts reliableDamage = hook.knownDamage(damage);
        var last=reliableDamage==null?null:reliableDamage.toJson();
        if(last!=null)last.addProperty("ago_s",Math.max(0,(p.getServer().getTicks()-damagedAt)/20));
        var spawn=p.getSpawnPointPosition(); var spawnWorld=p.getServer().getWorld(p.getSpawnPointDimension());
        String anchor=null;
        if(spawn!=null && spawnWorld!=null && spawnWorld.getChunkManager().getWorldChunk(spawn.getX()>>4,spawn.getZ()>>4)!=null) {
            var state=spawnWorld.getBlockState(spawn);
            if(state.isOf(Blocks.RESPAWN_ANCHOR))anchor="respawn_anchor";
            else if(state.isIn(net.minecraft.registry.tag.BlockTags.BEDS))anchor="bed";
        }
        var mount=p.getVehicle();
        return PlayerInfo.fromJson(object("name",p.getGameProfile().getName(),"uuid",p.getUuidAsString(),"pos",vector(p.getPos()),
            "pos_block",List.of(p.getBlockX(),p.getBlockY(),p.getBlockZ()),"dimension",p.getServerWorld().getRegistryKey().getValue().toString(),
            "yaw",p.getYaw(),"pitch",p.getPitch(),"facing",VecUtil.facing(p.getYaw()),"velocity",vector(v),"speed_mps",speed,
            "on_ground",p.isOnGround(),"in_water",p.isTouchingWater(),"in_lava",p.isInLava(),"in_powder_snow",p.inPowderSnow,"submerged",p.isSubmergedInWater(),
            "sprinting",p.isSprinting(),"sneaking",p.isSneaking(),"swimming",p.isSwimming(),"gliding",p.isFallFlying(),"flying",p.getAbilities().flying,
            "fall_flying",p.isFallFlying(),"climbing",p.isClimbing(),"on_fire",p.isOnFire(),"frozen_ticks",p.getFrozenTicks(),"sleeping",p.isSleeping(),
            "riding",mount==null?null:facts("type",Registries.ENTITY_TYPE.getId(mount.getType()).toString(),"name",mount.hasCustomName()?mount.getCustomName().getString():null),
            "health",p.getHealth(),"max_health",p.getMaxHealth(),"absorption",p.getAbsorptionAmount(),"armor",p.getArmor(),"toughness",p.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS),
            "food",hunger.getFoodLevel(),"saturation",hunger.getSaturationLevel(),"exhaustion",hunger.getExhaustion(),"air",p.getAir(),"max_air",p.getMaxAir(),
            "xp_level",p.experienceLevel,"xp_progress",p.experienceProgress,"xp_total",p.totalExperience,"fire_ticks",p.getFireTicks(),"fall_distance",p.fallDistance,
            "last_damage",last==null?null:Facts.fromJson(last),"effects",effects,
            "attributes",facts("minecraft:generic.movement_speed",p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),"minecraft:generic.attack_damage",p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)),
            "held",ItemCapture.read(p.getMainHandStack(),p.getInventory().selectedSlot),"offhand",ItemCapture.read(p.getOffHandStack(),40),"selected_slot",p.getInventory().selectedSlot,
            "respawn",spawn==null?null:facts("pos",List.of(spawn.getX(),spawn.getY(),spawn.getZ()),"dimension",p.getSpawnPointDimension().getValue().toString(),"anchor",anchor),
            "game_mode",p.interactionManager.getGameMode().getName()));
    }
}

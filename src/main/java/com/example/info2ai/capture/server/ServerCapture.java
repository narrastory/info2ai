package com.example.info2ai.capture.server;
import com.example.info2ai.util.NameZh;
import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.model.*;
import com.example.info2ai.staticworld.GenerationHandle;
import com.example.info2ai.staticworld.GenerationTrust;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
/** Registered by the common entry point, active only during the integrated-server lifecycle. */
public final class ServerCapture {
    private static ServerCapture active;
    private final MinecraftServer server;
    private final SnapshotStore store;
    private final long worldEpoch;
    private final DamageHook damageHook;
    private final SessionSection session;
    private final TimeSection time = new TimeSection();
    private final PlayerCoreSection player = new PlayerCoreSection();
    private final InventorySection inventory = new InventorySection();
    private final SurroundingsSection surroundings = new SurroundingsSection();
    private final BlockEntitySection blockEntities = new BlockEntitySection();
    private final DropsSection drops = new DropsSection();
    private final ProgressSection progress = new ProgressSection();
    private boolean loaded;
    private final ServerEventCapture eventCapture;
    private final Map<RegistryKey<World>,GenerationHandle> generationHandles=new HashMap<>();
    private ServerCapture(MinecraftServer server, SnapshotStore store) {
        this.server = server;
        this.store = store;
        worldEpoch = store.worldEpoch();
        session = new SessionSection(server);
        eventCapture = new ServerEventCapture(store,worldEpoch);
        store.staticGenerationContext(worldEpoch,GenerationTrust.builtinFacts());
        CaptureLog.discardPending();
        damageHook = new DamageHook(store);
        store.enableFresh(true);
        store.warning("capture_humidity_unavailable");
        store.warning("capture_client_pause_unavailable");
        if (!NameZh.available()) store.warning("capture_name_zh_unavailable");
    }
    public static void register(Info2AiConfig config) {
        if (!Info2AiApi.realCapture()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!server.isSingleplayer() || Info2AiApi.store() == null) return;
            try {
                active = new ServerCapture(server, Info2AiApi.store());
            } catch (RuntimeException failure) {
                Info2AiApi.store().warning("capture_init_failed");
                CaptureLog.error("服务端采集初始化失败", failure);
            }
        } );
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (active != null && active.server == server) {
                active.damageHook.stop();
                active.player.clearDamage();
                active.player.resetMovement();
                active.store.clearWorld(active.worldEpoch);
                active = null;
                CaptureLog.discardPending();
            }
        } );
        ServerWorldEvents.UNLOAD.register((server,world)-> {
            if(active!=null&&active.server==server) {
                active.generationHandles.remove(world.getRegistryKey());
                active.store.clearStaticSource();
            }
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success)-> {
            if(active!=null&&active.server==server) {
                active.generationHandles.clear();
                active.store.clearStaticSource();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerCapture capture = active;
            if (capture == null || capture.server != server) return;
            capture.damageHook.finishTick();
            if (!capture.damageHook.available()) capture.player.clearDamage();
            var host=server.getHostProfile();
            var observed=host==null?null:server.getPlayerManager().getPlayer(host.getId());
            if(observed!=null && capture.owns(observed))capture.eventCapture.tick(observed);
            var pending = capture.store.takeFreshRequests(capture.worldEpoch);
            if (server.getTicks() % 4 == 0 || !pending.isEmpty()) {
                var accepted = capture.collect();
                capture.store.completeFresh(pending,accepted);
            }
        } );
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            ServerCapture capture = active;
            if (capture != null && entity instanceof ServerPlayerEntity p && capture.owns(p)) {
                eventFact(p,()->ServerFactHooks.action(p,com.example.info2ai.events.Observation.ActionType.KILL,"player",1,0,
                    source.getTypeRegistryEntry().getKey().map(k->k.getValue().toString()).orElse(null),null));
            }
        } );
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)-> {
            com.example.info2ai.director.DirectorEvents.death(entity, source);
            if(entity instanceof ServerPlayerEntity)return;
            if(source.getAttacker() instanceof ServerPlayerEntity p)eventFact(p,()->ServerFactHooks.action(p,
                com.example.info2ai.events.Observation.ActionType.KILL,
                net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType()).toString(),1,0,
                source.getTypeRegistryEntry().getKey().map(k->k.getValue().toString()).orElse(null),null));
        } );
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world,p,pos,state,be)-> {
            if(p instanceof ServerPlayerEntity owned) {
                ServerFactHooks.block(owned,com.example.info2ai.events.Observation.ActionType.BREAK,pos,state);
                com.example.info2ai.director.DirectorEvents.broken(owned, pos, state);
            }
        } );
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((p, origin, destination) -> {
            ServerCapture capture = active;
            if (capture != null && capture.owns(p)) {
                capture.time.dimensionChanged();
                capture.player.resetMovement();
            }
        } );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerCapture capture = active;
            if (capture != null && capture.server == server && server.isOnThread() && server.getHostProfile() != null
            && server.getHostProfile().getId().equals(handler.player.getUuid())) {
                capture.damageHook.stop();
                capture.player.clearDamage();
                capture.player.resetMovement();
            }
        } );
    }
    private boolean owns(ServerPlayerEntity p) {
        return server.isOnThread() && !p.isDisconnected() && p.getServer() == server && server.getHostProfile() != null
        && server.getHostProfile().getId().equals(p.getUuid());
    }
    /** Called only at PlayerEntity.applyDamage HEAD; no state is read for a non-owned player. */
    public static void beginAppliedDamage(ServerPlayerEntity p) {
        ServerCapture capture = active;
        if (capture == null || !capture.owns(p)) return;
        capture.damageHook.begin(p.getHealth(), p.getAbsorptionAmount());
    }
    /** Optional RETURN may be absent: incomplete HEAD probes expire at END_SERVER_TICK. */
    public static void endAppliedDamage(ServerPlayerEntity p, DamageSource source) {
        ServerCapture capture = active;
        if (capture == null || !capture.owns(p)) return;
        try {
            AppliedDamage applied = capture.damageHook.finish(p.getHealth(), p.getAbsorptionAmount());
            if (applied != null && applied.amount() > 0) {
                com.example.info2ai.director.DirectorEvents.damage(p, source.getAttacker(), applied.amount());
                capture.player.damaged(p, source, applied);
                eventFact(p,()->new com.example.info2ai.events.Observation.Action(com.example.info2ai.events.Observation.ActionType.DAMAGE,"player",1,
                    applied.amount(),source.getTypeRegistryEntry().getKey().map(k->k.getValue().toString()).orElse(null),
                    source.getAttacker()==null?null:net.minecraft.registry.Registries.ENTITY_TYPE.getId(source.getAttacker().getType()).toString(),
                    true,true,false,false,java.util.List.of(p.getX(),p.getY(),p.getZ()),com.example.info2ai.events.EventData.of("blocks",
                    (double)p.fallDistance,"health_amount",applied.health(),"absorption_amount",applied.absorption())));
            }
        } catch (RuntimeException failure) {
            capture.damageHook.fail();
            CaptureLog.error("玩家实际伤害采集失败", failure);
        }
        if (!capture.damageHook.available()) capture.player.clearDamage();
    }
    public static void eventFact(ServerPlayerEntity p,java.util.function.Supplier<com.example.info2ai.events.Observation.Action> fact) {
        ServerCapture capture=active;
        if(capture==null||!capture.owns(p)||capture.store.worldEpoch()!=capture.worldEpoch||capture.store.paused())return;
        capture.eventCapture.beginFact();
        try {
            var observed=fact.get();
            if(observed!=null) {
                var data=observed.details()==null?new com.google.gson.JsonObject():observed.details().toJson();
                data.addProperty("dimension",p.getServerWorld().getRegistryKey().getValue().toString());
                observed=new com.example.info2ai.events.Observation.Action(observed.type(),observed.id(),observed.count(),observed.amount(),
                    observed.source(),observed.attacker(),observed.success(),observed.attributed(),observed.stone(),observed.crop(),observed.pos(),
                    com.example.info2ai.model.Facts.fromJson(data));
                capture.eventCapture.fact(observed);
            }
            capture.store.diagnostics().success(capture.worldEpoch,"capture_event_hook_failed");
        }
        catch(RuntimeException|LinkageError failure) {
            capture.store.diagnostics().failure(capture.worldEpoch,"capture_event_hook_failed",failure);
        }
        finally {
            capture.eventCapture.endFact();
        }
    }
    public static void portalObserved(net.minecraft.server.world.ServerWorld world,net.minecraft.util.math.BlockPos pos) {
        ServerCapture capture=active;
        if(capture==null||capture.server!=world.getServer()||!capture.server.isOnThread())return;
        var host=capture.server.getHostProfile();
        var p=host==null?null:capture.server.getPlayerManager().getPlayer(host.getId());
        if(p==null||p.getServerWorld()!=world)return;
        eventFact(p,()-> {
            if(pos==null||pos.getSquaredDistance(p.getPos())>128*128||world.getChunkManager().getWorldChunk(pos.getX()>>4,
                pos.getZ()>>4)==null||!world.getBlockState(pos).isOf(net.minecraft.block.Blocks.NETHER_PORTAL))return null;
            return new com.example.info2ai.events.Observation.Action(com.example.info2ai.events.Observation.ActionType.PORTAL,
                "minecraft:nether_portal",1,0,null,null,true,true,false,false,java.util.List.of((double)pos.getX(),(double)pos.getY(),(double)pos.getZ()),null);
        } );
    }
    private GenerationHandle generationHandle(ServerWorld world) {
        return generationHandles.computeIfAbsent(world.getRegistryKey(), key -> StaticGenerationContext.capture(world));
    }

    private SnapshotStore.ServerPublication collect() {
        if(store.worldEpoch()!=worldEpoch) {
            generationHandles.clear();
            return null;
        }
        if(store.paused())return null;
        var host = server.getHostProfile();
        if (host == null) return null;
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(host.getId());
        if (p == null || p.isDisconnected() || p.getServerWorld().getChunkManager().getWorldChunk(p.getBlockX() >> 4, p.getBlockZ() >> 4) == null) return null;
        // TODO(perf): 实测本整体路径冷启动 max 139.995ms、稳定滚动 max 5.845ms 超预算；
        // 尚无分段 profile，Phase9 前先定位采集/JSON/CRC/类加载等候选，再解决首帧与尖峰，不能猜测根因。
        long start = System.nanoTime();
        try {
            var s = surroundings.collect(p);
            eventCapture.environment(p.getServerWorld().getRegistryKey().getValue().toString(),s,surroundings.eventMobs(),surroundings.eventMobsMicros());
            var nearby = new SurroundingsInfo(s.biome(), s.biomeAtFeet(), s.light(), s.canSeeSky(), s.temperature(), s.humidity(), s.enclosedRatio(),
                s.caveDepth(),
            s.structuresNearby(), s.entities(), s.entityCounts(), blockEntities.collect(p), drops.collect(p), null, null, null);
            var director = Info2AiApi.director();
            var directorState = director instanceof com.example.info2ai.director.DirectorService service ? service.snapshot(p) : null;
            var sample = Snapshot.builder().tick(p.getServerWorld().getTime()).detail(Detail.FULL)
            .session(session.collect(server, p)).world(time.collect(p.getServerWorld(),
                server.getTicks())).player(eventCapture.player(player.collect(p, damageHook))).inventory(inventory.collect(p))
            .surroundings(nearby).signals(eventCapture.signals()).progress(progress.collect(server, p))
            .capabilities(new Capabilities(true, false, false, false, false, null, true, false, false, false, false,
                director.available())).director(directorState).build();
            var settings=Info2AiConfig.get().worldgen();
            var query=new com.example.info2ai.staticworld.StaticQuery(worldEpoch,sample.session().worldId(),sample.session().seed(),
                sample.session().mcVersion(),sample.world().dimensionId(),"builtin",
                p.getBlockX(),p.getBlockY(),p.getBlockZ(),settings.radius(),settings.enabled(),
                settings.enabled()?generationHandle(p.getServerWorld()):null);
            var accepted=store.publishServer(sample,worldEpoch,query);
            if(accepted==null)return null;
            if (!loaded) loaded=store.emit(worldEpoch,eventCapture.loaded(server.getSaveProperties().getLevelName(),(p.getServerWorld().getTimeOfDay()/24000+1)));
            store.diagnostics().success(worldEpoch,"capture_failed");
            return accepted;
        } catch (RuntimeException failure) {
            store.diagnostics().failure(worldEpoch,"capture_failed",failure);
            return null;
        } finally {
            long us = (System.nanoTime() - start) / 1000;
            store.recordServerCapture(worldEpoch,us);
            if (store.worldEpoch()==worldEpoch && us > 1500) {
                SnapshotStore destination=store;
                long expected=worldEpoch;
                CaptureLog.debug("capture slow: " + us + "us",()->destination.worldEpoch()==expected);
            }
        }
    }
}

package com.example.info2ai.capture.server;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.events.*;
import com.example.info2ai.events.Observation.*;
import com.example.info2ai.model.*;
import com.example.info2ai.util.JsonUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.stat.Stats;
import net.minecraft.screen.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.LootableInventory;
import java.util.*;
/** 仅服务端线程适配：每 tick 读基础值；环境重采样仍在原四 tick 路径。 */
public final class ServerEventCapture {
    private final SnapshotStore store;
    private final long epoch;
    private final EventEngine events=new EventEngine();
    private final SignalEngine signals=new SignalEngine();
    private final ActivityInference activity=new ActivityInference();
    private final EventEnvironmentProbe environmentProbe=new EventEnvironmentProbe();
    private final ArrayDeque<Action> pending=new ArrayDeque<>();
    private String environmentDimension;
    private SurroundingsInfo environment;
    private List<Mob> eventMobs=List.of();
    private Observation current;
    private List<Signal> evaluated=List.of();
    private long pendingSourceMicros,hookStarted;
    private int hookDepth;
    private int entityId=Integer.MIN_VALUE;
    private String playerId;
    private ScreenHandler opened;
    private long lastInputTick;
    private Double previousX,previousY,previousZ;
    private Float previousYaw,previousPitch;
    public ServerEventCapture(SnapshotStore store,long epoch) {
        this.store=store;
        this.epoch=epoch;
    }
    public void beginFact() {
        if(hookDepth++==0)hookStarted=System.nanoTime();
    }
    public void endFact() {
        if(--hookDepth==0)pendingSourceMicros+=(System.nanoTime()-hookStarted)/1000;
    }
    public void fact(Action fact) {
        if(pending.size()>=512) {
            store.diagnostics().unavailable(epoch,"capture_event_fact_overflow");
            return;
        }
        if(fact.type()==ActionType.PLACE||fact.type()==ActionType.BREAK)environmentProbe.invalidate();
        pending.addLast(fact);
    }
    public void environment(String dimension,SurroundingsInfo value,List<Mob> mobs,long sourceMicros) {
        long start=System.nanoTime();
        environmentDimension=dimension;
        environment=value;
        eventMobs=List.copyOf(mobs);
        pendingSourceMicros+=sourceMicros+(System.nanoTime()-start)/1000;
    }
    public void tick(ServerPlayerEntity p) {
        if(store.worldEpoch()!=epoch||store.paused()) {
            pending.clear();
            return;
        }
        long start=System.nanoTime();
        try {
            if(!Objects.equals(playerId,p.getUuidAsString())) {
                events.reset();
                activity.reset();
                signals.reset();
                playerId=p.getUuidAsString();
                current=null;
                opened=null;
                lastInputTick=p.getServer().getTicks();
            }
            // 重生保留 death→respawn 差分；实体更换时清除旧的容器/视角引用。
            if(entityId!=p.getId()) {
                entityId=p.getId();
                opened=null;
                previousX=null;
                previousY=null;
                previousZ=null;
                previousYaw=null;
                previousPitch=null;
            }
            current=null;
            current=observe(p);
            activity.observe(current);
            var emitted=events.tick(current,System.nanoTime()/1_000_000);
            if(emitted.stream().anyMatch(EventEngine::interruptsMining))activity.interruptMining();
            for(EventDraft event:emitted)store.emit(epoch,event);
            store.diagnostics().success(epoch,"capture_event_engine_failed");
        } catch(RuntimeException|LinkageError failure) {
            pending.clear();
            evaluated=List.of();
            store.diagnostics().failure(epoch,"capture_event_engine_failed",failure);
        }
        finally {
            store.recordEventEngine(epoch,(System.nanoTime()-start)/1000+pendingSourceMicros);
            pendingSourceMicros=0;
        }
        if(current!=null&&p.getServer().getTicks()%4==0) {
            start=System.nanoTime();
            try {
                evaluated=signals.evaluate(current,activity);
                store.diagnostics().success(epoch,"capture_signal_engine_failed");
            }
            catch(RuntimeException|LinkageError failure) {
                evaluated=List.of();
                store.diagnostics().failure(epoch,"capture_signal_engine_failed",failure);
            }
            finally {
                store.recordSignalEngine(epoch,(System.nanoTime()-start)/1000);
            }
        }
    }
    public PlayerInfo player(PlayerInfo base) {
        if(current==null)return base;
        var json=base.toJson();
        json.add("activity",activity.current().facts().toJson());
        return PlayerInfo.fromJson(json);
    }
    public List<Signal> signals() {
        return evaluated;
    }
    public EventDraft loaded(String world,long day) {
        return new EventDraft(current==null?0:current.tick(),"world_loaded","info","进入存档「"+world+"」，第 "+day+" 天",EventData.of("world",world,"day",
        day),current==null?null:current.pos(),0);
    }
    private Observation observe(ServerPlayerEntity p) {
        var world=p.getServerWorld();
        String dimension=world.getRegistryKey().getValue().toString();
        List<Action> actions=new ArrayList<>(pending);
        pending.clear();
        Vital vital=observeVital(p);
        Snapshot snapshot=store.current();
        boolean matching=snapshot!=null&&snapshot.world()!=null&&dimension.equals(snapshot.world().dimensionId())&&snapshot.player()!=null&&p.getUuidAsString().equals(snapshot.player().uuid());
        Client client=observeClient(p,actions,matching?snapshot:null);
        var inventory=observeInventory(p,actions);
        var env=observeEnvironment(p,dimension);
        boolean mobsKnown=dimension.equals(environmentDimension)&&environment!=null;
        var bosses=observeBosses(matching?snapshot:null);
        var memory=store.history();
        var biomeMemory=memory==null||snapshot==null||snapshot.session()==null?null:
            memory.biomeMemory(snapshot.session().worldId(),env.biome());
        boolean firstBiomeKnown=biomeMemory!=null&&biomeMemory.absoluteFirstKnown();
        var staticInfo=matching&&snapshot.world()!=null?snapshot.world().staticWorld():null;
        var structures=com.example.info2ai.staticworld.StaticProjection.observations(staticInfo,dimension,p.getX(),p.getZ());
        return new Observation(world.getTime(),p.getUuidAsString(),dimension,List.of(p.getX(),p.getY(),p.getZ()),vital,env,inventory,client,actions,
        mobsKnown?eventMobs:List.of(),bosses==null?List.of():bosses,structures,staticInfo!=null,firstBiomeKnown,mobsKnown,bosses!=null);
    }
    private Vital observeVital(ServerPlayerEntity p) {
        Map<String,Integer> effects=new HashMap<>();
        for(var e:p.getStatusEffects())effects.put(e.getEffectType().getKey().map(k->k.getValue().toString()).orElse("unknown"),
        e.isInfinite()?-1:e.getDuration()/20);
        String held=p.getMainHandStack().isDamageable()?Registries.ITEM.getId(p.getMainHandStack().getItem()).toString():"";
        String equipment=held+"|"+p.getInventory().armor.stream()
            .map(stack->Registries.ITEM.getId(stack.getItem()).toString()).toList();
        var mount=p.getVehicle();
        return new Vital(p.getHealth(),p.getMaxHealth(),p.getHungerManager().getFoodLevel(),p.getAir(),p.isSubmergedInWater(),p.isInLava(),
        p.isOnFire(),p.isDead(),p.isSleeping(),p.experienceLevel,p.totalExperience,
        p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST)),effects,
        mount==null?null:Registries.ENTITY_TYPE.getId(mount.getType()).toString(),equipment);
    }
    private Client observeClient(ServerPlayerEntity p,List<Action> actions,Snapshot snapshot) {
        long tick=p.getServer().getTicks();
        var world=p.getServerWorld();
        boolean moved=previousX!=null&&(p.getX()!=previousX||p.getY()!=previousY||p.getZ()!=previousZ||p.getYaw()!=previousYaw||p.getPitch()!=previousPitch);
        boolean playerAction=actions.stream().anyMatch(a->a.confirmed()&&Set.of(ActionType.BREAK,ActionType.PLACE,ActionType.ATTACK,ActionType.OPEN,
            ActionType.ENCHANT,ActionType.CRAFT,ActionType.ANIMAL).contains(a.type()));
        if(moved||playerAction)lastInputTick=tick;
        previousX=p.getX();
        previousY=p.getY();
        previousZ=p.getZ();
        previousYaw=p.getYaw();
        previousPitch=p.getPitch();
        var clientPlayer=snapshot==null?null:snapshot.player();
        var inputs=clientPlayer==null||clientPlayer.inputs()==null?null:clientPlayer.inputs().toJson();
        Long idle=inputs==null?null:JsonUtil.longNumber(inputs,"idle_ticks");
        // 完整键鼠观察缺失时保持未知；位移/视角/事实动作总能反证 AFK。
        if(idle!=null)idle=Math.min(idle,tick-lastInputTick);
        String screen=screen(p.currentScreenHandler);
        return new Client(screen,idle,fishingTick(snapshot,world.getTime()),java.time.LocalTime.now().getHour());
    }
    private Observation.Inventory observeInventory(ServerPlayerEntity p,List<Action> actions) {
        var world=p.getServerWorld();
        int free=0;
        boolean food=false,light=false,bed=false;
        for(var stack:p.getInventory().main) {
            if(stack.isEmpty()) {
                free++;
                continue;
            }
            String id=Registries.ITEM.getId(stack.getItem()).toString();
            food|=stack.contains(DataComponentTypes.FOOD);
            bed|=id.endsWith("_bed");
            light|=lightSource(stack);
        }
        // 副手也属于玩家携带物品，避免把副手食物/火把误判为不存在。
        var off=p.getOffHandStack();
        food|=off.contains(DataComponentTypes.FOOD);
        light|=lightSource(off);
        bed|=Registries.ITEM.getId(off.getItem()).getPath().endsWith("_bed");
        var held=p.getMainHandStack();
        Double spawnDistance=null;
        if(p.getSpawnPointPosition()!=null&&p.getSpawnPointDimension().equals(world.getRegistryKey()))spawnDistance=Math.sqrt(p.getSpawnPointPosition().getSquaredDistance(p.getPos()));
        else if(p.getSpawnPointPosition()==null&&world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD))spawnDistance=Math.sqrt(world.getSpawnPos().getSquaredDistance(p.getPos()));
        List<String> loot=new ArrayList<>();
        if(opened!=p.currentScreenHandler) {
            String openedKind=ContainerKinds.opened(opened==null?null:opened.getClass(),
                p.currentScreenHandler==null?null:p.currentScreenHandler.getClass(),true);
            if(openedKind!=null) {
                Inventory inv=containerInventory(p.currentScreenHandler);
                if(inv!=null&&(!(inv instanceof LootableInventory l)||l.getLootTable()==null)) {
                    for(int slot=0;slot<inv.size();slot++) {
                        var stack=inv.getStack(slot);
                        String id=Registries.ITEM.getId(stack.getItem()).toString();
                        if(Set.of("minecraft:diamond","minecraft:emerald","minecraft:netherite_ingot","minecraft:enchanted_golden_apple",
                        "minecraft:ancient_debris").contains(id))loot.add(id);
                    }
                }
                actions.add(ServerFactHooks.action(p,ActionType.OPEN,openedKind,1,0,null,null));
            }
            opened=p.currentScreenHandler;
        }
        return new Observation.Inventory(free,Registries.ITEM.getId(held.getItem()).toString(),
        held.isDamageable()?held.getMaxDamage()-held.getDamage():0,held.isDamageable()?held.getMaxDamage():0,food,light,bed,spawnDistance,loot);
    }
    private Environment observeEnvironment(ServerPlayerEntity p,String dimension) {
        var world=p.getServerWorld();
        SurroundingsInfo s=dimension.equals(environmentDimension)?environment:null;
        var lightFacts=s==null||s.light()==null?null:s.light().toJson();
        Double fallHeight=environmentProbe.fallHeight(p);
        return new Environment(s==null?null:s.biome(),s==null?null:s.enclosedRatio(),JsonUtil.integer(lightFacts,"sky"),
        JsonUtil.integer(lightFacts,"block"),s==null?null:s.canSeeSky(),s==null||s.caveDepth()==null?null:s.caveDepth().doubleValue(),
        Math.floorMod(world.getTimeOfDay(),24000),world.getTimeOfDay(),world.isRaining(),world.isThundering(),
        p.getServer().getSaveProperties().getLevelName(),environmentProbe.nearbyBed(p),fallHeight,fallHeight==null?null:fallHeight<=.01);
    }
    private List<Boss> observeBosses(Snapshot snapshot) {
        List<Boss> bosses=new ArrayList<>();
        if(snapshot==null||snapshot.surroundings()==null||snapshot.surroundings().hud()==null)return null;
        var bars=snapshot.surroundings().hud().toJson().get("boss_bars");
        if(bars==null||!bars.isJsonArray())return null;
        for(var entry:bars.getAsJsonArray()) {
            var bar=entry.getAsJsonObject();
            String name=JsonUtil.string(bar,"name");
            bosses.add(new Boss(JsonUtil.string(bar,"id"),name,bar.get("progress").getAsDouble()));
        }
        return bosses;
    }
    private static boolean lightSource(net.minecraft.item.ItemStack stack) {
        if(stack.isEmpty())return false;
        return stack.getItem() instanceof net.minecraft.item.BlockItem block&&block.getBlock().getDefaultState().getLuminance()>0;
    }
    private static Long fishingTick(Snapshot snapshot,long tick) {
        if(snapshot==null||snapshot.surroundings()==null||snapshot.surroundings().sounds()==null)return null;
        for(var sound:snapshot.surroundings().sounds()) {
            var s=sound.toJson();
            String id=JsonUtil.string(s,"id");
            Double ago=JsonUtil.number(s,"ago_s");
            if(id!=null&&id.contains("fishing")&&ago!=null)return tick-Math.round(ago*20);
        }
        return null;
    }
    private static Inventory containerInventory(ScreenHandler h) {
        if(h instanceof GenericContainerScreenHandler c)return c.getInventory();
        // 其他界面只记录已经打开，不为稀有物品主动访问不明容器。
        return null;
    }
    private static String screen(ScreenHandler h) {
        if(h instanceof EnchantmentScreenHandler)return "enchanting";
        if(h instanceof CraftingScreenHandler)return "crafting";
        if(h instanceof AbstractFurnaceScreenHandler)return "furnace";
        if(h instanceof SmithingScreenHandler)return "smithing";
        if(h instanceof GenericContainerScreenHandler)return "chest";
        if(h instanceof ShulkerBoxScreenHandler)return "shulker_box";
        if(h instanceof HopperScreenHandler)return "hopper";
        return null;
    }
}

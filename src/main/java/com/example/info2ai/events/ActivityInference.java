package com.example.info2ai.events;
import java.util.*;
import com.example.info2ai.model.Facts;
import static com.example.info2ai.events.Observation.*;
/** 1200 个游戏 tick 的增量窗口；每个样本只入队和出队一次。 */
public final class ActivityInference {
    /** 距离最后一次确认破坏达到10游戏秒，持续挖矿区间结束。 */
    public static final long MINING_IDLE_TIMEOUT_TICKS=200;
    public record Activity(String kind,double confidence,int sinceS,String note) {
        public Facts facts() {
            return EventData.of("kind",kind,"confidence",confidence,"since_s",sinceS,"note",note);
        }
    }
    private record Sample(long tick,int attacks,int damage,int opens,int breaks,int stones,int places,int farms,int novel,double distance) {
    }
    private final ArrayDeque<Sample> window=new ArrayDeque<>(),combat=new ArrayDeque<>();
    private final Set<String> biomes=new HashSet<>(),structures=new HashSet<>();
    private Observation previous;
    private int attacks,damage,opens,breaks,stones,places,farms,novel;
    private double distance;
    private long since,miningSince,lastMiningBreak=-1;
    private int miningCount;
    private Activity current=new Activity("idle",.5,0,"近期没有足够活动证据");
    public void reset() {
        window.clear();
        combat.clear();
        biomes.clear();
        structures.clear();
        previous=null;
        attacks=damage=opens=breaks=stones=places=farms=novel=0;
        distance=0;
        since=0;
        miningSince=0;
        lastMiningBreak=-1;
        miningCount=0;
        current=new Activity("idle",.5,0,"近期没有足够活动证据");
    }
    public void observe(Observation o) {
        if(previous!=null&&(!previous.playerId().equals(o.playerId())||o.tick()<previous.tick()))reset();
        if(previous!=null&&o.tick()==previous.tick())return;
        if(previous!=null&&!previous.dimension().equals(o.dimension())) {
            window.clear();
            combat.clear();
            attacks=damage=opens=breaks=stones=places=farms=novel=0;
            distance=0;
            previous=null;
            interruptMining();
        }
        expireMining(o.tick());
        int a=0,d=0,op=0,b=0,s=0,p=0,f=0,n=0;
        for(Action fact:o.actions())if(fact.confirmed()) {
            switch(fact.type()) {
                case ATTACK -> a+=fact.count();
                case DAMAGE -> d+=fact.count();
                case OPEN -> op+=fact.count();
                case BREAK -> {
                    expireMining(o.tick());
                    if(miningCount==0)miningSince=o.tick();
                    miningCount+=fact.count();
                    lastMiningBreak=o.tick();
                    b+=fact.count();
                    if(fact.stone())s+=fact.count();
                    if(fact.crop())f+=fact.count();
                }
                case PLACE -> {
                    p+=fact.count();
                    if(fact.crop())f+=fact.count();
                }
                case ANIMAL -> f+=fact.count();
                default -> {
                }
            }
        }
        if(o.actions().stream().anyMatch(fact->fact.confirmed()&&fact.type()!=ActionType.BREAK)||d>0)interruptMining();
        if(d==0&&previous!=null&&o.vital()!=null&&previous.vital()!=null&&o.vital().health()<previous.vital().health())d=1;
        if(o.env()!=null&&o.env().biome()!=null&&biomes.add(o.env().biome())&&previous!=null)n++;
        for(Structure structure:o.structures())if(structures.add(o.dimension()+":"+structure.key()))n++;
        double step=previous==null?0:Math.hypot(o.pos().get(0)-previous.pos().get(0),o.pos().get(2)-previous.pos().get(2));
        Sample sample=new Sample(o.tick(),a,d,op,b,s,p,f,n,step);
        window.addLast(sample);
        combat.addLast(sample);
        add(sample,1);
        attacks+=a;
        damage+=d;
        while(!window.isEmpty()&&o.tick()-window.getFirst().tick()>=1200)add(window.removeFirst(),-1);
        while(!combat.isEmpty()&&o.tick()-combat.getFirst().tick()>=200) {
            var old=combat.removeFirst();
            attacks-=old.attacks();
            damage-=old.damage();
        }
        String kind;
        String screen=o.client()==null?null:o.client().screen();
        if(attacks>0||damage>=2)kind="fighting";
        else if(o.client()!=null&&o.client().idleTicks()!=null&&o.client().idleTicks()>1800&&step==0)kind="afk";
        else if("enchanting".equals(screen))kind="enchanting";
        else if(screen!=null&&Set.of("crafting","furnace","smithing").contains(screen))kind="crafting";
        else if(opens>=2)kind="looting";
        else if(breaks>=15&&stones>=breaks*.7)kind="mining";
        else if(places>=10&&o.dimension().equals("minecraft:overworld"))kind="building";
        else if(farms>0)kind="farming";
        else if(o.inv()!=null&&"minecraft:fishing_rod".equals(o.inv().heldId())&&o.client()!=null&&o.client().fishingSoundTick()!=null&&o.tick()-o.client().fishingSoundTick()<600&&distance<8)kind="fishing";
        else if(distance>=120)kind="traveling";
        else if(distance>=60&&novel>0)kind="exploring";
        else kind=distance<8?"idle":"traveling";
        if(previous==null||!current.kind().equals(kind))since=o.tick();
        current=new Activity(kind,kind.equals("idle")?.5:.85,(int)((o.tick()-since)/20),note(kind));
        previous=o;
    }
    private void add(Sample s,int sign) {
        opens+=s.opens()*sign;
        breaks+=s.breaks()*sign;
        stones+=s.stones()*sign;
        places+=s.places()*sign;
        farms+=s.farms()*sign;
        novel+=s.novel()*sign;
        distance+=s.distance()*sign;
    }
    private static String note(String kind) {
        return switch(kind) {
            case "fighting" -> "近期有战斗伤害记录";
            case "mining" -> "近期主要破坏石头类方块";
            case "building" -> "近期在主世界放置方块";
            case "crafting" -> "正在使用制作界面";
            case "enchanting" -> "正在使用附魔界面";
            case "looting" -> "近期多次打开容器";
            case "farming" -> "近期有作物或动物交互记录";
            case "fishing" -> "手持钓竿，附近近期有钓鱼音效";
            case "traveling" -> "近期在移动";
            case "exploring" -> "移动期间观察到本次会话的新区域";
            case "afk" -> "超过90秒没有输入或移动";
            default -> "近期位移小于8格";
        };
    }
    public Activity current() {
        return current;
    }
    public int broken() {
        return breaks;
    }
    public double distance() {
        return distance;
    }
    private void expireMining(long tick) {
        if(miningCount>0&&!miningActiveAt(tick))interruptMining();
    }
    public boolean miningActiveAt(long tick) {
        return miningCount>0&&tick>=lastMiningBreak&&tick-lastMiningBreak<MINING_IDLE_TIMEOUT_TICKS;
    }
    public long lastMiningBreak() {return lastMiningBreak;}
    public void interruptMining() {
        miningCount=0;miningSince=0;lastMiningBreak=-1;
    }
    public int miningCount() {
        return miningCount;
    }
    public long miningSince() {
        return miningSince;
    }
}

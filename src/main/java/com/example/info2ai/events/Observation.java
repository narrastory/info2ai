package com.example.info2ai.events;
import com.example.info2ai.model.Facts;
import java.util.*;
/** 每 tick 的小型不可变事实；可空字段表示来源未知，不能当作否定。 */
public record Observation(long tick, String playerId, String dimension, List<Double> pos,
Vital vital, Environment env, Inventory inv, Client client, List<Action> actions,
List<Mob> mobs, List<Boss> bosses, List<Structure> structures, boolean structuresKnown, boolean firstBiomeKnown,boolean mobsKnown,boolean bossesKnown) {
    public Observation(long tick,String playerId,String dimension,List<Double> pos,Vital vital,Environment env,Inventory inv,Client client,
    List<Action> actions,List<Mob> mobs,List<Boss> bosses,List<Structure> structures,boolean structuresKnown,boolean firstBiomeKnown) {
        this(tick,playerId,dimension,pos,vital,env,inv,client,actions,mobs,bosses,structures,structuresKnown,firstBiomeKnown,true,true);
    }
    public Observation {
        pos=List.copyOf(pos);
        actions=List.copyOf(actions);
        mobs=List.copyOf(mobs);
        bosses=List.copyOf(bosses);
        structures=List.copyOf(structures);
    }
    public record Vital(double health,double maxHealth,int food,int air,boolean submerged,boolean lava,boolean fire,
    boolean dead,boolean sleeping,int xpLevel,int xpTotal,int restTicks,Map<String,Integer> effects,String mount,String equipment) {
        public Vital {
            effects=Map.copyOf(effects);
        }
    }
    public record Environment(String biome,Double enclosed,Integer skyLight,Integer blockLight,Boolean seeSky,Double caveDepth,
    long dayTime,long worldTime,boolean raining,boolean thundering,String worldName,Boolean bedNearby,Double fallHeight,Boolean groundSupport) {
    }
    public record Inventory(int free,String heldId,int durability,int maxDurability,Boolean hasFood,Boolean hasLight,
    Boolean hasBed,Double spawnDistance,List<String> rareLoot) {
        public Inventory {
            rareLoot=List.copyOf(rareLoot);
        }
    }
    public record Client(String screen,Long idleTicks,Long fishingSoundTick,Integer localHour) {
    }
    public record Mob(String uuid,String id,double distance,boolean hostile,boolean targeting) {
    }
    public record Boss(String uuid,String name,double percent) {
    }
    public record Structure(String key,String id,double distance,String direction,Integer x,Integer z,String source,String confidence,Boolean visited,
            String type,String variantStatus) {
        public Structure(String key,String id,double distance,String direction,Integer x,Integer z,String source,String confidence,Boolean visited) {
            this(key,id,distance,direction,x,z,source,confidence,visited,null,null);
        }
        public Structure(String key,String id,double distance,String direction) {this(key,id,distance,direction,null,null,null,null,null);}
        public String displayId(){return id!=null?id:type;}
        public Facts facts(String dimension) {
            var data=EventData.of("key",key,"id",id,"dist",distance,"dir",direction,"dimension",dimension,
                "absolute_first_known",false).toJson();
            if(x!=null)data.addProperty("x",x);if(z!=null)data.addProperty("z",z);
            if(type!=null) {
                data.addProperty("type",type);data.addProperty("structure_id",id);data.addProperty("variant_status",variantStatus);
            }
            if(source!=null) {data.addProperty("source",source);data.addProperty("confidence",confidence);
                data.addProperty("visited_scope","observed_proximity_to_predicted_start");}
            return Facts.fromJson(data);
        }
    }
    public enum ActionType {
        DAMAGE,ATTACK,KILL,BREAK,PLACE,TOOL_BREAK,OPEN,ENCHANT,ADVANCEMENT,RECIPE,CRAFT,ANIMAL,PORTAL,SLEEP_COMPLETE,DROP
    }
    public record Action(ActionType type,String id,int count,double amount,String source,String attacker,
    boolean success,boolean attributed,boolean stone,boolean crop,List<Double> pos,Facts details) {
        public Action {
            if(pos!=null)pos=List.copyOf(pos);
        }
        public boolean confirmed() {
            return success && attributed;
        }
    }
}

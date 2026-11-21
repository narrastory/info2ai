package com.example.info2ai.events;
import java.util.Map;
/** 毫秒冷却唯一真源；未列出的 kind 无冷却。 */
public final class EventCooldowns {
    private EventCooldowns() {
    }
    public static final Map<String,Long> VALUES=Map.ofEntries(
    Map.entry("player_damaged",500L),Map.entry("player_healed",3000L),Map.entry("low_health_entered",10000L),Map.entry("low_health_exited",10000L),
    Map.entry("near_death",30000L),Map.entry("fell_in_lava",10000L),Map.entry("on_fire",5000L),Map.entry("drowning",5000L),Map.entry("took_fall_damage",1000L),
    Map.entry("hungry",30000L),Map.entry("starving",30000L),Map.entry("entered_cave",60000L),Map.entry("exited_cave",60000L),
    Map.entry("reached_surface",60000L),
    Map.entry("biome_changed",30000L),Map.entry("altitude_record",60000L),Map.entry("mob_killed",200L),Map.entry("hostiles_nearby",15000L),
    Map.entry("creeper_nearby",10000L),Map.entry("entity_targeting_player",20000L),Map.entry("player_attacked_mob",200L),Map.entry("mining_streak",300000L),
    Map.entry("mining_drought",300000L),Map.entry("tool_low",120000L),Map.entry("chest_opened",2000L),Map.entry("inventory_full",120000L),
    Map.entry("equipment_changed",3000L),Map.entry("recipe_unlocked",5000L),Map.entry("afk_started",120000L));
}

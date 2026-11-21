package com.example.info2ai.events;
/** 每种事实领域独立维护轻量差分；冷却统一交给引擎。 */
public interface EventDetector {
    void tick(EventContext context);
    String kind();
    default long cooldownMs() {
        return EventCooldowns.VALUES.getOrDefault(kind(),0L);
    }
}

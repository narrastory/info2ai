package com.example.info2ai.staticworld;
import com.example.info2ai.model.StaticWorldInfo;
public final class NoopStaticSource implements StaticWorldSource {
    public boolean isAvailable(StaticQuery query) {return false;}
    public StaticWorldInfo getStatic(StaticQuery query) {return null;}
    public String describe() {return "unavailable";}
}

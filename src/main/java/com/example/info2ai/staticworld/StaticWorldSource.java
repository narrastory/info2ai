package com.example.info2ai.staticworld;
import com.example.info2ai.model.StaticWorldInfo;

/** Blocking source methods are for the one static worker only. */
public interface StaticWorldSource {
    boolean isAvailable(StaticQuery query) throws Exception;
    StaticWorldInfo getStatic(StaticQuery query) throws Exception;
    /** Nonblocking metadata for a source available for every supported query. */
    default String availableConfidence() { return null; }
    String describe();
}

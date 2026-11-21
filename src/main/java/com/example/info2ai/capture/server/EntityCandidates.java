package com.example.info2ai.capture.server;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Internal retention follows the HTTP request maximum, independently of its response default. */
public final class EntityCandidates {
    private EntityCandidates() { }

    public static <T> List<T> retain(List<T> values, ToDoubleFunction<T> distance) {
        return values.stream().sorted(Comparator.comparingDouble(distance)).limit(256).toList();
    }
}

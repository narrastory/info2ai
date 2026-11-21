package com.example.info2ai.director.policy;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 由解析器提供的指令结构，不持有游戏对象。 */
public record CommandShape(String raw, List<Segment> segments) {
    public CommandShape {
        segments = List.copyOf(segments);
    }

    public List<String> roots() {
        return segments.stream().filter(Segment::rootCommand).filter(segment -> !segment.path().isEmpty())
            .map(segment -> segment.path().getFirst()).toList();
    }

    public record BlockPosition(int x, int y, int z) {
        public List<Integer> coordinates() {
            return List.of(x, y, z);
        }
    }

    public record Segment(boolean rootCommand, List<String> path, Map<String, String> argsRaw,
                          Map<String, Object> argsResolved) {
        public Segment {
            path = List.copyOf(path);
            argsRaw = Map.copyOf(argsRaw);
            argsResolved = argsResolved.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> immutableValue(entry.getValue())));
        }

        private static Object immutableValue(Object value) {
            return switch (value) {
                case BlockPosition position -> position;
                case String text -> text;
                case Integer integer -> integer;
                case Long number -> number;
                case Double number -> number;
                case Float number -> number;
                case Boolean flag -> flag;
                case Collection<?> values -> values.stream().map(Segment::immutableValue).toList();
                default -> throw new IllegalArgumentException("不支持的指令参数类型");
            };
        }
    }
}

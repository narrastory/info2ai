package com.example.info2ai.director.policy;

import com.example.info2ai.director.policy.CommandShape.BlockPosition;
import com.example.info2ai.director.policy.CommandShape.Segment;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShapeExtractor {
    private static final Set<String> POSITIONS = Set.of("from", "to", "begin", "end", "destination", "pos");

    private ShapeExtractor() { }

    public static CommandShape extract(ParseResults<ServerCommandSource> parsed, RootCommandNode<ServerCommandSource> root) {
        String input = parsed.getReader().getString();
        List<Segment> segments = new ArrayList<>();
        for (var context = parsed.getContext(); context != null; context = context.getChild()) {
            var nodes = context.getNodes();
            if (nodes.isEmpty()) continue;
            var first = nodes.getFirst().getNode();
            boolean rootCommand = root.getChild(first.getName()) == first;
            List<String> path = new ArrayList<>();
            Map<String, String> raw = new HashMap<>();
            Map<String, Object> resolved = new HashMap<>();
            for (var node : nodes) {
                String name = node.getNode().getName();
                if (node.getNode() instanceof ArgumentCommandNode<?, ?>) {
                    path.add("<" + name + ">");
                    var argument = context.getArguments().get(name);
                    if (argument != null) {
                        raw.put(name, argument.getRange().get(input));
                        resolve(name, argument.getResult(), context.getSource(), resolved);
                    }
                } else {
                    path.add(name);
                }
            }
            segments.add(new Segment(rootCommand, path, raw, resolved));
        }
        return new CommandShape(input, segments);
    }

    private static void resolve(String name, Object argument, ServerCommandSource source, Map<String, Object> values) {
        switch (argument) {
            case PosArgument position when POSITIONS.contains(name) -> {
                var block = position.toAbsoluteBlockPos(source);
                values.put(name, new BlockPosition(block.getX(), block.getY(), block.getZ()));
            }
            case RegistryEntry.Reference<?> entry -> values.put(name, entry.registryKey().getValue().toString());
            case RegistryKey<?> key -> values.put(name, key.getValue().toString());
            case NbtCompound nbt when name.equals("nbt") -> values.put(name, nbt.getKeys());
            case Integer number -> values.put(name, number);
            case Double number -> values.put(name, number);
            default -> { }
        }
    }
}

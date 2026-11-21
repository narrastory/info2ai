package com.example.info2ai.director.catalog;

import com.example.info2ai.api.DirectorRequests.CommandEntry;
import com.example.info2ai.api.DirectorRequests.HelpIndex;
import com.example.info2ai.api.DirectorRequests.HelpResult;
import com.example.info2ai.director.policy.CommandPolicy;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CommandCatalog(CommandNotes notes, Map<String, List<String>> usage) {
    public static CommandCatalog build(CommandDispatcher<ServerCommandSource> dispatcher, ServerCommandSource source,
                                       CommandNotes notes) {
        var usage = new LinkedHashMap<String, List<String>>();
        for (var node : dispatcher.getRoot().getChildren()) {
            snapshot(node, node.getName(), source, dispatcher, usage);
        }
        return new CommandCatalog(notes, Map.copyOf(usage));
    }

    private static void snapshot(CommandNode<ServerCommandSource> node, String path, ServerCommandSource source,
                                 CommandDispatcher<ServerCommandSource> dispatcher, Map<String, List<String>> usage) {
        var lines = new ArrayList<String>();
        if (node.getCommand() != null) lines.add(path);
        dispatcher.getSmartUsage(node, source).values().stream().distinct().sorted()
            .limit(Math.max(0, 60 - lines.size())).map(line -> path + " " + line).forEach(lines::add);
        usage.put(path, List.copyOf(lines));
        for (var child : node.getChildren()) {
            if (child instanceof LiteralCommandNode<?>) snapshot(child, path + " " + child.getName(), source, dispatcher, usage);
        }
    }

    public HelpResult help(String path, String topic, CommandPolicy policy) {
        if (topic != null) {
            String value = notes.topic(topic);
            return value == null ? null : new HelpResult(null, null, null, List.of(), value);
        }
        if (path != null) {
            String key = String.join(" ", path.strip().split("\\s+"));
            var lines = usage.get(key);
            if (lines == null) return null;
            String root = key.split(" ", 2)[0];
            String blocked = policy.rootDenial(root);
            return new HelpResult(key, blocked == null, blocked, lines, notes.command(root));
        }
        var allowed = new ArrayList<CommandEntry>();
        var denied = new ArrayList<CommandEntry>();
        usage.keySet().stream().filter(key -> !key.contains(" ")).sorted().forEach(root -> {
            String blocked = policy.rootDenial(root);
            if (blocked != null) denied.add(new CommandEntry(root, blocked));
            else {
                String note = notes.command(root);
                String label = note == null ? root : note.lines().findFirst().orElse(root).replaceFirst("^#+\\s*", "");
                allowed.add(new CommandEntry(root, label));
            }
        });
        return new HelpResult(null, null, null, List.of(), null,
            new HelpIndex(allowed, denied, notes.topics().keySet().stream().sorted().toList()));
    }
}

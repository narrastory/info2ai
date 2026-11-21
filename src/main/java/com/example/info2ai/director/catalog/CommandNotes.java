package com.example.info2ai.director.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public record CommandNotes(Map<String, String> commands, Map<String, String> topics, List<Example> examples) {
    private static final Pattern MARKER = Pattern.compile("(?m)^<!-- (cmd|topic): (.+?) -->[ \\t]*$");

    public record Example(String file, int line, String command) { }

    public static CommandNotes load() {
        var commands = new LinkedHashMap<String, String>();
        var topics = new LinkedHashMap<String, String>();
        var examples = new ArrayList<Example>();
        for (String name : List.of("guide.md", "commands.md")) {
            String contents = read(name);
            var markers = MARKER.matcher(contents);
            var offsets = new ArrayList<Integer>();
            var ends = new ArrayList<Integer>();
            var kinds = new ArrayList<String>();
            var aliases = new ArrayList<String>();
            while (markers.find()) {
                offsets.add(markers.start());
                ends.add(markers.end());
                kinds.add(markers.group(1));
                aliases.add(markers.group(2));
            }
            offsets.add(contents.length());
            for (int i = 0; i < ends.size(); i++) {
                String section = contents.substring(ends.get(i), offsets.get(i + 1)).strip();
                var target = kinds.get(i).equals("cmd") ? commands : topics;
                for (String alias : aliases.get(i).split(",")) target.put(alias.strip(), section);
            }
            boolean active = false;
            int number = 0;
            for (String line : contents.split("\\R", -1)) {
                number++;
                String text = line.strip();
                if (text.equals("```mcfunction")) active = true;
                else if (text.startsWith("```")) active = false;
                else if (active && !text.isEmpty() && !text.startsWith("#")) examples.add(new Example(name, number, text));
            }
        }
        return new CommandNotes(Map.copyOf(commands), Map.copyOf(topics), List.copyOf(examples));
    }

    private static String read(String name) {
        try (var stream = CommandNotes.class.getResourceAsStream("/assets/info2ai/director/" + name)) {
            if (stream == null) throw new IOException("缺少导演手册：" + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public String command(String root) { return commands.get(root); }
    public String topic(String topic) { return topics.get(topic); }
}

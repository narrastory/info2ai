package com.example.info2ai.util;

import com.example.info2ai.capture.server.CaptureLog;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chinese registry names from the installed game, independent of the display language. */
public final class NameZh {
    private static final List<String> PREFIXES = List.of("item", "block", "entity", "effect", "biome");
    private static volatile Map<String, String> names = Map.of();

    private NameZh() { }

    public static void initialize(Reader gameLocale) throws IOException {
        var translations = JsonParser.parseReader(gameLocale).getAsJsonObject();
        Map<String, String> loaded = new HashMap<>();
        for (String kind : PREFIXES) {
            String prefix = kind + ".minecraft.";
            for (var entry : translations.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(prefix) && key.indexOf('.', prefix.length()) < 0 && key.length() > prefix.length()) {
                    loaded.putIfAbsent("minecraft:" + key.substring(prefix.length()), entry.getValue().getAsString());
                }
            }
        }
        try (var stream = NameZh.class.getResourceAsStream("/assets/info2ai/lang/structure_names_zh_cn.json")) {
            if (stream == null) throw new IOException("缺少结构中文名称资源");
            var structures = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            structures.entrySet().forEach(entry -> loaded.putIfAbsent(entry.getKey(), entry.getValue().getAsString()));
        }
        names = Map.copyOf(loaded);
    }

    public static boolean available() {
        return !names.isEmpty();
    }

    public static String translated(String id) {
        return names.get(id);
    }

    public static String get(String id) {
        String translated = names.get(id);
        if (translated != null) return translated;
        CaptureLog.debug("缺少中文映射: " + id);
        return id.substring(id.indexOf(':') + 1);
    }
}

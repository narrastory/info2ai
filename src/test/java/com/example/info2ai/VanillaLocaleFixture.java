package com.example.info2ai;

import com.example.info2ai.util.NameZh;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class VanillaLocaleFixture {
    private VanillaLocaleFixture() { }

    static void initialize() throws IOException {
        Path assets = Path.of(System.getProperty("info2ai.testAssetsDirectory"));
        Path index = assets.resolve("indexes").resolve(System.getProperty("info2ai.testAssetsIndex") + ".json");
        String hash;
        try (var reader = Files.newBufferedReader(index)) {
            hash = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("objects")
                .getAsJsonObject("minecraft/lang/zh_cn.json").get("hash").getAsString();
        }
        try (var reader = Files.newBufferedReader(assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash))) {
            NameZh.initialize(reader);
        }
    }
}

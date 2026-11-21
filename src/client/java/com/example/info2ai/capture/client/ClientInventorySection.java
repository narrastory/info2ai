package com.example.info2ai.capture.client;
import com.example.info2ai.capture.server.ItemCapture;
import com.example.info2ai.model.InventoryInfo;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
public final class ClientInventorySection {
    public InventoryInfo collect(MinecraftClient client) {
        var cursor = ItemCapture.read(client.player.currentScreenHandler.getCursorStack(), null);
        JsonObject value = new JsonObject(); value.add("cursor", cursor == null ? JsonNull.INSTANCE : cursor.toJson());
        return InventoryInfo.fromJson(value);
    }
}

package com.example.info2ai.model;

import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/** 不可变的契约数据；不持有游戏对象。 */
public record Capabilities(
    Boolean serverCapture,
    Boolean clientCapture,
    Boolean screenshot,
    Boolean staticWorldAvailable,
    Boolean staticWorldReady,
    String staticWorldConfidence,
    Boolean history,
    Boolean chatWrite,
    Boolean overlayWrite,
    Boolean tts,
    Boolean paused,
    Boolean director
) implements JsonModel {
    public Capabilities(Boolean serverCapture, Boolean clientCapture, Boolean screenshot, Boolean staticWorldAvailable,
                        Boolean staticWorldReady, String staticWorldConfidence, Boolean history, Boolean chatWrite,
                        Boolean overlayWrite, Boolean tts, Boolean paused) {
        this(serverCapture, clientCapture, screenshot, staticWorldAvailable, staticWorldReady, staticWorldConfidence,
            history, chatWrite, overlayWrite, tts, paused, null);
    }
    public static Capabilities fromJson(JsonObject o) {
        if (o == null) return null;
        return new Capabilities(
            JsonUtil.bool(o, "server_capture"),
            JsonUtil.bool(o, "client_capture"),
            JsonUtil.bool(o, "screenshot"),
            JsonUtil.bool(o, "static_world_available"),
            JsonUtil.bool(o, "static_world_ready"),
            JsonUtil.string(o, "static_world_confidence"),
            JsonUtil.bool(o, "history"),
            JsonUtil.bool(o, "chat_write"),
            JsonUtil.bool(o, "overlay_write"),
            JsonUtil.bool(o, "tts"),
            JsonUtil.bool(o, "paused"),
            JsonUtil.bool(o, "director")
        );
    }

    @Override public JsonObject toJson(Detail detail) {
        JsonObject o = new JsonObject();
        JsonUtil.put(o, "server_capture", serverCapture, detail);
        JsonUtil.put(o, "client_capture", clientCapture, detail);
        JsonUtil.put(o, "screenshot", screenshot, detail);
        JsonUtil.put(o, "static_world_available", staticWorldAvailable, detail);
        JsonUtil.put(o, "static_world_ready", staticWorldReady, detail);
        JsonUtil.put(o, "static_world_confidence", staticWorldConfidence, detail);
        JsonUtil.put(o, "history", history, detail);
        JsonUtil.put(o, "chat_write", chatWrite, detail);
        JsonUtil.put(o, "overlay_write", overlayWrite, detail);
        JsonUtil.put(o, "tts", tts, detail);
        JsonUtil.put(o, "paused", paused, detail);
        if (director != null) o.addProperty("director", director);
        return o;
    }
}

package com.example.info2ai.http.handler;
import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.http.*;
import com.sun.net.httpserver.*;
import java.io.IOException;

public final class ScreenshotHandler implements HttpHandler {
    private final SnapshotStore store;
    private final Info2AiConfig config;
    public ScreenshotHandler(Info2AiConfig config, SnapshotStore store) { this.config = config; this.store = store; }
    @Override public void handle(HttpExchange exchange) throws IOException {
        var snapshot = store.current();
        if (snapshot == null) throw new ApiException(503, "not_in_world");
        if (store.paused()) throw new ApiException(503, "paused");
        var query = Query.parse(exchange);
        int width = (int)Query.number(query, "max_width", 960, 160, 3840);
        int quality = (int)Query.number(query, "quality", 85, 1, 100);
        boolean hideHud = Query.number(query, "hide_hud", 0, 0, 1) == 1;
        String format = query.getOrDefault("format", "png");
        if (!format.equals("png") && !format.equals("jpg")) throw new ApiException(400, "bad_format");
        if (config.mock() || snapshot.capabilities() == null || !Boolean.TRUE.equals(snapshot.capabilities().screenshot()))
            throw new ApiException(503, "screenshot_disabled");
        var result = Info2AiApi.client().captureScreen(width, hideHud, quality, format);
        byte[] bytes = result.bytes();
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "image/png"); headers.set("Cache-Control", "no-store");
        headers.set("X-Info2ai-Format", "png");
        headers.set("X-Info2ai-Seq", Long.toString(result.seq()));
        headers.set("X-Info2ai-RealTime", Long.toString(result.capturedAt()));
        headers.set("X-Info2ai-Hide-Hud-Supported", Boolean.toString(result.hideHudSupported()));
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}

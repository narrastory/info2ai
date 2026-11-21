package com.example.info2ai.http.handler;

import com.example.info2ai.api.DirectorRequests.RunRequest;
import com.example.info2ai.api.DirectorRequests.ActionsResult;
import com.example.info2ai.api.DirectorRequests.UndoRequest;
import com.example.info2ai.api.DirectorRequests.Visibility;
import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.http.ApiException;
import com.example.info2ai.http.Json;
import com.example.info2ai.http.Query;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class DirectorHandler {
    private static final int MAX_BODY_BYTES = 65_536;

    private DirectorHandler() { }

    public static void check(HttpExchange exchange) throws IOException {
        JsonObject request = read(exchange);
        String command = optionalString(request.get("command"));
        Json.write(exchange, 200, Info2AiApi.director().check(command).toJson());
    }

    public static void run(HttpExchange exchange) throws IOException {
        JsonObject request = read(exchange);
        String command = optionalString(request.get("command"));
        String intent = optionalString(request.get("intent"));
        if (intent == null || intent.isBlank() || intent.codePointCount(0, intent.length()) > 120) {
            throw new ApiException(400, "bad_intent");
        }
        Visibility visibility = Visibility.SIGNED;
        if (request.has("visibility")) {
            String value = optionalString(request.get("visibility"));
            if (value == null || !value.equals(value.toLowerCase(Locale.ROOT))) {
                throw new ApiException(400, "bad_visibility");
            }
            try {
                visibility = Visibility.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new ApiException(400, "bad_visibility");
            }
        }
        Json.write(exchange, 200, Info2AiApi.director().run(new RunRequest(command, intent, visibility)).toJson());
    }

    public static void status(HttpExchange exchange) throws IOException {
        Json.write(exchange, 200, Info2AiApi.director().status().toJson());
    }

    public static void actions(HttpExchange exchange) throws IOException {
        var query = Query.parse(exchange);
        int limit = (int) Query.number(query, "limit", 20, 1, 100);
        Json.write(exchange, 200, new ActionsResult(Info2AiApi.director().actions(limit)).toJson());
    }

    public static void help(HttpExchange exchange) throws IOException {
        var query = Query.parse(exchange);
        String command = query.get("command");
        String topic = query.get("topic");
        if (command != null && topic != null) throw new ApiException(400, "bad_help_query");
        Json.write(exchange, 200, Info2AiApi.director().help(command, topic).toJson());
    }

    public static void lookup(HttpExchange exchange) throws IOException {
        var query = Query.parse(exchange);
        int limit = (int) Query.number(query, "limit", 20, 1, 100);
        Json.write(exchange, 200, Info2AiApi.director().lookup(query.get("kind"), query.get("q"), limit).toJson());
    }

    public static void selftest(HttpExchange exchange) throws IOException {
        Json.write(exchange, 200, Info2AiApi.director().selftest());
    }

    public static void undo(HttpExchange exchange) throws IOException {
        JsonObject request = read(exchange);
        JsonElement id = request.get("action_id");
        JsonElement last = request.get("last");
        try {
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isNumber() && last == null) {
                long actionId = id.getAsBigDecimal().longValueExact();
                if (actionId > 0) {
                    Json.write(exchange, 200, Info2AiApi.director().undo(new UndoRequest(actionId, false)).toJson());
                    return;
                }
            }
        } catch (ArithmeticException invalid) {
            throw new ApiException(400, "bad_action_id");
        }
        if (id == null && last != null && last.isJsonPrimitive() && last.getAsJsonPrimitive().isBoolean() && last.getAsBoolean()) {
            Json.write(exchange, 200, Info2AiApi.director().undo(new UndoRequest(null, true)).toJson());
            return;
        }
        throw new ApiException(400, "bad_action_id");
    }

    private static JsonObject read(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new ApiException(413, "body_too_large");
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException invalid) {
            throw new ApiException(400, "invalid_json");
        }
    }

    private static String optionalString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
            ? value.getAsString() : null;
    }
}

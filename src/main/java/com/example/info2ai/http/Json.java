package com.example.info2ai.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class Json {
    private Json() { }
    public static JsonObject err(String code) { JsonObject value=new JsonObject();value.addProperty("error",code);return value; }
    public static JsonObject err(String code,String message) { JsonObject value=err(code);value.addProperty("message",message);return value; }
    public static void write(HttpExchange exchange,int status,JsonObject body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
        try { exchange.sendResponseHeaders(status,status==304?-1:bytes.length);if(status!=304)exchange.getResponseBody().write(bytes); }
        finally { exchange.close(); }
    }
}

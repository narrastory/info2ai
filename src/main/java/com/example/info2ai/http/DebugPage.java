package com.example.info2ai.http;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;

/** 自包含的人类可读状态页。页面本身不含数据，数据由页面带令牌请求现有接口获得。 */
final class DebugPage {
    private static final String RESOURCE="/assets/info2ai/debug/index.html";
    private static volatile byte[] cached;
    private DebugPage() { }

    static boolean isShell(String path) { return path.equals("/debug") || path.equals("/debug/"); }

    static void write(HttpExchange exchange) throws IOException {
        byte[] body=load();
        var headers=exchange.getResponseHeaders();
        headers.set("Content-Type","text/html; charset=utf-8");
        headers.set("Cache-Control","no-store");
        headers.set("X-Frame-Options","DENY");
        headers.set("Referrer-Policy","no-referrer");
        headers.set("Content-Security-Policy",
            "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'");
        try { exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body); }
        finally { exchange.close(); }
    }

    private static byte[] load() throws IOException {
        byte[] value=cached;
        if(value!=null)return value;
        try(InputStream in=DebugPage.class.getResourceAsStream(RESOURCE)) {
            if(in==null)throw new ApiException(500,"debug_page_missing");
            value=in.readAllBytes();
        }
        cached=value;
        return value;
    }
}

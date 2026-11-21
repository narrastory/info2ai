package com.example.info2ai.http;

import com.example.info2ai.config.Info2AiConfig;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class AuthFilter extends Filter {
    private final Info2AiConfig config;
    private long windowStarted;
    private int denied;
    public AuthFilter(Info2AiConfig config) { this.config=config; }
    @Override public String description() { return "本地 Bearer 鉴权"; }
    @Override public void doFilter(HttpExchange exchange,Chain chain) throws IOException {
        String path=exchange.getRequestURI().getPath();
        // 状态页外壳不含任何数据；页面里的所有数据请求仍需 Bearer 令牌。
        if(path.equals("/health")||DebugPage.isShell(path)) { chain.doFilter(exchange);return; }
        String header=exchange.getRequestHeaders().getFirst("Authorization");
        String candidate=header!=null && header.startsWith("Bearer ")?header.substring(7):"";
        if(!MessageDigest.isEqual(config.token().getBytes(StandardCharsets.UTF_8),candidate.getBytes(StandardCharsets.UTF_8))) {
            logDenial();
            Json.write(exchange,401,Json.err("unauthorized"));return;
        }
        chain.doFilter(exchange);
    }
    private synchronized void logDenial() {
        long now=System.nanoTime();
        if(now-windowStarted>=60_000_000_000L) { windowStarted=now;denied=0; }
        if(denied++<5)org.slf4j.LoggerFactory.getLogger("info2ai.http").warn("HTTP 鉴权失败；检查本机令牌配置。");
    }
}

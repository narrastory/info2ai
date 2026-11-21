package com.example.info2ai.http;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.config.Info2AiConfig;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;

public final class HttpServerHost implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService mockTimer;
    private final SnapshotStore store;
    private volatile boolean running=true;
    private HttpServerHost(HttpServer server,Info2AiConfig config,SnapshotStore store) {
        this.server=server;this.store=store;
        var context=server.createContext("/",new Routes(config,store,server.getAddress().getPort()));
        context.getFilters().add(new Filter() {
            @Override public String description() { return "所有响应的协议元数据"; }
            @Override public void doFilter(HttpExchange exchange,Chain chain) throws IOException {
                exchange.getResponseHeaders().set("X-Info2ai-Schema","1");
                exchange.getResponseHeaders().set("X-Info2ai-Port",Integer.toString(server.getAddress().getPort()));
                exchange.getResponseHeaders().set("X-Info2ai-Seq",Long.toString(store.sequence()));
                chain.doFilter(exchange);
            }
        });
        context.getFilters().add(new AuthFilter(config));
        server.setExecutor(executor);
        server.start();
        if(config.mock()) {
            mockTimer=Executors.newSingleThreadScheduledExecutor(task->{ Thread thread=new Thread(task,"info2ai-mock");thread.setDaemon(true);return thread; });
            mockTimer.scheduleAtFixedRate(()->store.publish(MockSnapshotFactory.create()),5,5,TimeUnit.SECONDS);
        } else mockTimer=null;
    }
    public static HttpServerHost start(Info2AiConfig config,SnapshotStore store) throws IOException {
        IOException last=null;
        for(int offset=0;offset<=10;offset++) {
            try { return new HttpServerHost(HttpServer.create(new InetSocketAddress("127.0.0.1",config.port()==0?0:config.port()+offset),0),config,store); }
            catch(BindException occupied) { last=occupied; }
        }
        throw new IOException("info2ai 端口范围被占用",last);
    }
    public int actualPort() { return server.getAddress().getPort(); }
    public boolean isRunning() { return running; }
    @Override public synchronized void close() {
        if(!running)return;
        running=false;store.polls().closeAll();
        if(mockTimer!=null)mockTimer.shutdownNow();
        server.stop(0);executor.shutdownNow();
    }
}

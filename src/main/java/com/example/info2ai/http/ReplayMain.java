package com.example.info2ai.http;

import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.config.Info2AiConfig;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** Pure JVM entry: no Fabric/Minecraft class initialization or capture callbacks. */
public final class ReplayMain {
    private ReplayMain() { }
    public static void main(String[] args) throws Exception {
        if(System.getProperty("info2ai.replay")==null)throw new IllegalArgumentException("-Dinfo2ai.replay=<recording directory> is required");
        var config=Info2AiConfig.load(Path.of(System.getProperty("info2ai.configDir","run/replay/config")));
        Info2AiApi.start(config);
        Runtime.getRuntime().addShutdownHook(new Thread(Info2AiApi::stop,"info2ai-replay-stop"));
        System.out.println("info2ai REPLAY: http://127.0.0.1:"+Info2AiApi.actualPort());
        new CountDownLatch(1).await();
    }
}

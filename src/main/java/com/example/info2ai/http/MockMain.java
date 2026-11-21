package com.example.info2ai.http;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.config.Info2AiConfig;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** 纯 JVM 入口，运行接口联调无需启动 Minecraft。 */
public final class MockMain {
    private MockMain() { }
    public static void main(String[] args) throws Exception {
        System.setProperty("info2ai.mock","true");
        Path directory=Path.of(System.getProperty("info2ai.configDir","run/mock/config"));
        Info2AiConfig config=Info2AiConfig.load(directory);
        com.example.info2ai.api.Info2AiApi.start(config);
        Runtime.getRuntime().addShutdownHook(new Thread(com.example.info2ai.api.Info2AiApi::stop,"info2ai-mock-stop"));
        System.out.println("info2ai MOCK: http://127.0.0.1:"+com.example.info2ai.api.Info2AiApi.actualPort()+"; token file: "+config.directory().resolve("info2ai.token"));
        new CountDownLatch(1).await();
    }
}

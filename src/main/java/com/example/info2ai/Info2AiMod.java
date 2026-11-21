package com.example.info2ai;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.director.DirectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Info2AiMod implements ModInitializer {
    public static final String MOD_ID = "info2ai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            LOGGER.warn("info2ai 当前只支持单人客户端，已禁用服务。");
            return;
        }
        try {
            Info2AiConfig config = Info2AiConfig.load(FabricLoader.getInstance().getGameDir().resolve("config"));
            Info2AiApi.start(config);
            if (Info2AiApi.realCapture()) {
                com.example.info2ai.capture.server.ServerCapture.register(config);
                DirectorService.register(config);
            }
            LOGGER.info("info2ai HTTP 已启动: 127.0.0.1:{}；令牌位于 config/info2ai.token；mock={}", Info2AiApi.actualPort(), config.mock());
        } catch (Exception failure) {
            if (Info2AiApi.store() != null) Info2AiApi.store().warning("mod_init_failed");
            LOGGER.error("info2ai 初始化失败，游戏继续运行。", failure);
        }
    }
}

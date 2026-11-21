package com.example.info2ai;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.util.NameZh;
import java.io.IOException;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class Info2AiClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Info2AiMod.LOGGER.debug("info2ai client initialized.");
        if (Info2AiApi.realCapture()) {
            registerChineseNames();
            com.example.info2ai.input.DirectorKey.register();
        }
        try { if(Info2AiApi.realCapture())com.example.info2ai.capture.client.ClientCapture.register(); }
        catch (RuntimeException | LinkageError failure) {
            if (Info2AiApi.store()!=null) Info2AiApi.store().diagnostics().failure(Info2AiApi.store().worldEpoch(),"capture_client_init_failed",failure);
        }
        // HTTP 关闭/后台发布线程清理可能等待合并，不能在 render 线程等待。
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
            Thread.ofPlatform().name("info2ai-shutdown").start(Info2AiApi::stop));
    }

    private static void registerChineseNames() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() {
                return Identifier.of(Info2AiMod.MOD_ID, "chinese_names");
            }

            @Override public void reload(ResourceManager resources) {
                var locale = resources.getResource(Identifier.ofVanilla("lang/zh_cn.json"));
                if (locale.isEmpty()) {
                    Info2AiMod.LOGGER.warn("未找到游戏自带的简体中文资源");
                    return;
                }
                try (var reader = locale.get().getReader()) {
                    NameZh.initialize(reader);
                } catch (IOException | RuntimeException failure) {
                    Info2AiMod.LOGGER.warn("无法读取游戏的简体中文名称", failure);
                }
            }
        });
    }
}

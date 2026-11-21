package com.example.info2ai.screenshot;

import com.example.info2ai.api.*;
import com.example.info2ai.capture.client.ClientCapture;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.http.ApiException;
import com.example.info2ai.model.Facts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import java.io.IOException;

/** PNG 编码也在 render 线程完成；任何路径都释放 native buffer，不触发递归渲染。 */
public final class ScreenshotService implements ClientBridge {
    private final MinecraftClient client;
    private final ClientCapture capture;
    private final SnapshotStore store;
    private final CaptureCoordinator coordinator;
    public ScreenshotService(MinecraftClient client,ClientCapture capture,SnapshotStore store) {
        this.client=client; this.capture=capture; this.store=store;
        coordinator=new CaptureCoordinator(client::execute,client::isOnThread,System::nanoTime,2000);
    }
    @Override
    public boolean gamePaused() { return client.isPaused(); }

    @Override public ScreenshotResult captureScreen(int maxWidth,boolean hideHud,int quality,String format) {
        if (Info2AiApi.mock()) throw new ApiException(503,"screenshot_disabled");
        var scope=capture.attachment();
        return coordinator.capture(()->capture.valid(scope),()->capturePixels(scope,maxWidth),store.diagnostics(),scope==null?store.worldEpoch():scope.epoch());
    }
    private ScreenshotResult capturePixels(ClientCapture.Attachment scope,int maxWidth) {
        if (!client.isOnThread() || client.world!=scope.world() || client.player==null || !client.isInSingleplayer())
            throw new ApiException(503,"no_client");
        var snapshot=store.current();
        if (snapshot==null || snapshot.session()==null || !scope.worldId().equals(snapshot.session().worldId())
            || !client.world.getRegistryKey().getValue().toString().equals(snapshot.world().dimensionId())) throw new ApiException(503,"no_client");
        // TODO(perf): 原 R5 max 89.444ms；7dadbc6 的 JFR 截图采样主要位于 STB PNG 编码。
        // 此处改用快速无损编码，整条路径仍完整计时；60ms 预算须由新包实机复测确认。
        long started=System.nanoTime();
        JsonObject context=new JsonObject();
        context.addProperty("world_id",scope.worldId()); context.addProperty("dimension",snapshot.world().dimensionId());
        JsonArray position=new JsonArray(); position.add(client.player.getX()); position.add(client.player.getY()); position.add(client.player.getZ());
        context.add("pos",position); context.addProperty("yaw",client.player.getYaw()); context.addProperty("pitch",client.player.getPitch());
        var framebuffer=client.getFramebuffer();
        try (NativeImage raw=new NativeImage(framebuffer.textureWidth,framebuffer.textureHeight,false)) {
            // 与 1.21.1 takeScreenshot 的读取/翻转步骤一致；从分配起即持有资源，读取失败也能关闭。
            RenderSystem.bindTexture(framebuffer.getColorAttachment());
            raw.loadFromTextureImage(0,true);
            raw.mirrorVertically();
            int width=raw.getWidth(), height=raw.getHeight();
            int targetWidth=Math.min(maxWidth,width);
            int targetHeight=Math.max(1,(int)Math.round(height*(targetWidth/(double)width)));
            LosslessPngEncoder.validateDimensions(targetWidth,targetHeight);
            byte[] bytes;
            if (targetWidth==width) bytes=LosslessPngEncoder.encode(width,height,raw.copyPixelsRgba());
            else try (NativeImage scaled=new NativeImage(targetWidth,targetHeight,false)) {
                raw.resizeSubRectTo(0,0,width,height,scaled);
                bytes=LosslessPngEncoder.encode(targetWidth,targetHeight,scaled.copyPixelsRgba());
            }
            return new ScreenshotResult(bytes,snapshot.seq(),System.currentTimeMillis(),Facts.fromJson(context),false);
        } catch (IOException failure) { throw new ApiException(503,"screenshot_failed",null,failure); }
        finally { store.recordScreenshot(scope.epoch(),(System.nanoTime()-started)/1000); }
    }
}

package com.example.info2ai.capture.client;

import com.example.info2ai.api.*;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.model.*;
import com.example.info2ai.screenshot.ScreenshotService;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/** 游戏线程仅采集不可变切片并写原子邮箱；合并/CRC 在独立发布线程完成。 */
public final class ClientCapture {
    public record Attachment(long epoch, String worldId, ClientWorld world) { }
    private final SnapshotStore store;
    private final ClientPlayerSection player = new ClientPlayerSection();
    private final ClientInventorySection inventory = new ClientInventorySection();
    private final ClientSurroundSection surroundings = new ClientSurroundSection();
    private volatile Attachment attachment;
    private ClientCapture(SnapshotStore store) { this.store=store; }
    public static void register() {
        if (Info2AiApi.mock() || Info2AiApi.store()==null) return;
        var capture=new ClientCapture(Info2AiApi.store());
        Info2AiApi.client(new ScreenshotService(MinecraftClient.getInstance(),capture,capture.store));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->capture.attach(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->capture.detach());
        ClientTickEvents.END_CLIENT_TICK.register(capture::tick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->capture.detach());
    }
    public Attachment attachment() { return attachment; }
    /** HTTP 可调用：仅比较已发布引用/代数，不读取游戏对象。 */
    public boolean valid(Attachment expected) {
        return expected!=null && attachment==expected && store.worldEpoch()==expected.epoch() && store.current()!=null && !store.paused();
    }
    private void attach(MinecraftClient client) {
        if (client.player==null || client.world==null || !client.isInSingleplayer()) return;
        Snapshot snapshot=store.current();
        // 未确认服务端身份时延迟，不把上个存档或普通多人连接标为本世界。
        if (snapshot==null || snapshot.session()==null || snapshot.session().worldId()==null || snapshot.world()==null || snapshot.capabilities()==null
            || !Boolean.TRUE.equals(snapshot.capabilities().serverCapture())
            || !client.world.getRegistryKey().getValue().toString().equals(snapshot.world().dimensionId())) return;
        Attachment current=attachment;
        if (current!=null && current.world()==client.world && current.epoch()==store.worldEpoch()) return;
        attachment=new Attachment(store.worldEpoch(),snapshot.session().worldId(),client.world);
        ClientObservations.attach(store.diagnostics(),attachment.epoch());
    }
    public void detach() {
        Attachment previous=attachment;
        attachment=null; ClientObservations.reset();
        if (previous!=null) store.invalidateWorld(previous.epoch());
    }
    private void tick(MinecraftClient client) {
        if (client.player==null || client.world==null || !client.isInSingleplayer()) { detach(); return; }
        long started=System.nanoTime();
        // 维度切换只撤销客户端附件；服务端仍持有同一个存档代数。
        if (attachment!=null && attachment.world()!=client.world) { attachment=null; ClientObservations.reset(); }
        attach(client);
        Attachment scope=attachment;
        if (!valid(scope)) return;
        try {
            Snapshot server=store.current();
            if (server==null || server.session()==null || server.world()==null
                || !scope.worldId().equals(server.session().worldId())
                || !client.world.getRegistryKey().getValue().toString().equals(server.world().dimensionId())) return;
            JsonObject session=new JsonObject(); session.addProperty("world_id",scope.worldId());
            session.addProperty("paused",client.isPaused());
            Snapshot sample=Snapshot.builder().tick(client.world.getTime()).session(SessionInfo.fromJson(session)).world(server.world())
                .player(player.collect(client)).inventory(inventory.collect(client)).surroundings(surroundings.collect(client,store,scope.epoch()))
                .capabilities(new Capabilities(false,true,true,false,false,null,false,false,false,false,false)).build();
            store.publishClient(sample,scope.epoch());
            store.diagnostics().success(scope.epoch(),"capture_client_failed");
        } catch (RuntimeException | LinkageError failure) { store.diagnostics().failure(scope.epoch(),"capture_client_failed",failure); }
        finally { store.recordClientCapture(scope.epoch(),(System.nanoTime()-started)/1000); }
    }
}

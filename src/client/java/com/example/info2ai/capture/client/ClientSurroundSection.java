package com.example.info2ai.capture.client;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.mixin.*;
import com.example.info2ai.model.*;
import com.example.info2ai.util.JsonUtil;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.util.*;

public final class ClientSurroundSection {
    private final ClientEntitySection entities = new ClientEntitySection();
    public SurroundingsInfo collect(MinecraftClient client, SnapshotStore store,long epoch) {
        long now = System.currentTimeMillis();
        var p = client.player;
        var heard = ClientObservations.SOUNDS.recent(p.getX(),p.getY(),p.getZ(),now);
        List<Facts> sounds = null;
        if (heard != null) {
            sounds = new ArrayList<>();
            for (var sound : heard) {
                double dx=sound.x()-p.getX(), dy=sound.y()-p.getY(), dz=sound.z()-p.getZ();
                JsonObject value=new JsonObject(); value.addProperty("id",sound.id());
                value.addProperty("distance",JsonUtil.round1(Math.sqrt(dx*dx+dy*dy+dz*dz)));
                value.addProperty("ago_s",JsonUtil.round1((now-sound.at())/1000.0));
                value.addProperty("direction",direction(dx,dz)); sounds.add(Facts.fromJson(value));
            }
        }
        availability(store,epoch,"capture_sound_hook_unconfirmed",heard!=null);
        JsonObject hud = new JsonObject();
        boolean hudKnown = false;
        try {
            if (client.inGameHud instanceof InGameHudAccessor access) {
                hud.add("title",text(access.info2ai$titleTicks()>0?access.info2ai$title():null));
                hud.add("subtitle",text(access.info2ai$titleTicks()>0?access.info2ai$subtitle():null));
                hud.add("actionbar",text(access.info2ai$overlayTicks()>0?access.info2ai$overlay():null));
                hudKnown = true;
                store.diagnostics().success(epoch,"capture_hud_accessor_failed");
            }
        } catch (RuntimeException | LinkageError failure) { hudKnown=false;store.diagnostics().failure(epoch,"capture_hud_accessor_failed",failure); }
        availability(store,epoch,"capture_hud_accessor_unavailable",hudKnown);
        boolean bossesKnown = false;
        try {
            if (client.inGameHud.getBossBarHud() instanceof BossBarHudAccessor access) {
                JsonArray bars=new JsonArray();
                for (var entry : access.info2ai$bossBars().entrySet()) {
                    var bar=entry.getValue();
                    JsonObject value=new JsonObject(); value.addProperty("id",entry.getKey().toString());value.addProperty("name",bar.getName().getString());
                    value.addProperty("progress",bar.getPercent()); value.addProperty("color",bar.getColor().getName()); bars.add(value);
                }
                hud.add("boss_bars",bars); bossesKnown=true;
                store.diagnostics().success(epoch,"capture_bossbar_accessor_failed");
            }
        } catch (RuntimeException | LinkageError failure) { bossesKnown=false;store.diagnostics().failure(epoch,"capture_bossbar_accessor_failed",failure); }
        if (!bossesKnown) hud.add("boss_bars",JsonNull.INSTANCE);
        availability(store,epoch,"capture_bossbar_accessor_unavailable",bossesKnown);
        var notices = ClientObservations.toasts(now);
        JsonArray toasts = new JsonArray();
        if (notices != null) for (var notice : notices) {
            JsonObject value=new JsonObject(); value.addProperty("type",notice.type()); value.addProperty("text",notice.text());
            value.addProperty("ago_s",JsonUtil.round1((now-notice.at())/1000.0)); toasts.add(value);
        }
        hud.add("toasts",notices==null?JsonNull.INSTANCE:toasts);
        availability(store,epoch,"capture_toast_hook_unconfirmed",notices!=null);
        availability(store,epoch,"capture_toast_text_unavailable",notices==null || notices.stream().noneMatch(notice->notice.text()==null));
        if (!hudKnown) { hud.add("title",JsonNull.INSTANCE); hud.add("subtitle",JsonNull.INSTANCE); hud.add("actionbar",JsonNull.INSTANCE); }
        JsonObject performance = new JsonObject();
        performance.addProperty("fps",client.getCurrentFps()); performance.addProperty("frame_time_ms",client.getRenderTime()/1_000_000.0);
        performance.add("chunk_updates",JsonNull.INSTANCE);
        store.diagnostics().unavailable(epoch,"capture_chunk_updates_unavailable");
        var rendered=entities.rendered(now);
        JsonUtil.putNullable(performance,"rendered_entity_ids",rendered);
        performance.addProperty("rendered_window_ms",250);
        performance.addProperty("rendered_limit",128);
        availability(store,epoch,"capture_entity_render_hook_unconfirmed",rendered!=null);
        return new SurroundingsInfo(null,null,null,null,null,null,null,null,null,null,null,null,null,sounds,Facts.fromJson(hud),Facts.fromJson(performance));
    }
    private static JsonElement text(Text text) { return text==null?JsonNull.INSTANCE:new JsonPrimitive(text.getString()); }
    private static void availability(SnapshotStore store,long epoch,String code,boolean known) { if(known)store.diagnostics().success(epoch,code);else store.diagnostics().unavailable(epoch,code); }
    private static String direction(double x,double z) {
        String[] points={"S","SW","W","NW","N","NE","E","SE"};
        int index=(int)Math.floor((Math.toDegrees(Math.atan2(-x,z))+22.5+360)%360/45);
        return points[index];
    }
}

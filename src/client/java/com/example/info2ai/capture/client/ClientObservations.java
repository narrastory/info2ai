package com.example.info2ai.capture.client;

import com.example.info2ai.capture.merge.CaptureDiagnostics;
import java.util.*;

/** 回调先持有本次附件的纯值 Context，旧回调不能记录或诊断新的世界。 */
public final class ClientObservations {
    public record Notice(String type,String text,long at) { }
    public record Context(CaptureDiagnostics diagnostics,long epoch) { }
    public static final com.example.info2ai.events.InputActivity INPUT=new com.example.info2ai.events.InputActivity();
    public static final SoundHistory SOUNDS=new SoundHistory();
    private static final ArrayDeque<Notice> hud=new ArrayDeque<>(),toasts=new ArrayDeque<>();
    private static final LinkedHashMap<Integer,Long> rendered=new LinkedHashMap<>();
    private static volatile Context context;
    private static boolean toastConfirmed,renderConfirmed;
    private ClientObservations() { }
    public static Context context() { return context; }
    public static synchronized void attach(CaptureDiagnostics diagnostics,long epoch) {
        reset();context=new Context(diagnostics,epoch);
    }
    public static synchronized void reset() {
        context=null;INPUT.reset();SOUNDS.clear();hud.clear();toasts.clear();rendered.clear();
        toastConfirmed=false;renderConfirmed=false;
    }
    public static synchronized void input(int channel,long window) {
        var owner=context;
        if(owner==null)return;
        try {
            var client=net.minecraft.client.MinecraftClient.getInstance();
            if(client.world==null||!client.isOnThread()||!client.isWindowFocused()||window!=client.getWindow().getHandle())return;
            INPUT.input(channel,client.world.getTime());
        } catch(RuntimeException|LinkageError failure) { owner.diagnostics().failure(owner.epoch(),"capture_input_hook_failed",failure);INPUT.reset(); }
    }
    public static synchronized Long idleTicks(long tick) { return INPUT.idleTicks(tick); }
    public static synchronized void hud(Context owner,String type,String text) {
        if(context!=owner)return;
        if(hud.size()==32)hud.removeFirst();hud.addLast(new Notice(type,text,System.currentTimeMillis()));
        owner.diagnostics().success(owner.epoch(),"capture_hud_"+type+"_hook_failed");
    }
    public static synchronized void toast(Context owner,String type,String text) {
        if(context!=owner)return;
        toastConfirmed=true;if(toasts.size()==12)toasts.removeFirst();
        toasts.addLast(new Notice(type,text,System.currentTimeMillis()));
        owner.diagnostics().success(owner.epoch(),"capture_toast_hook_failed");
    }
    public static synchronized void sound(Context owner,String id,boolean music,boolean relative,double x,double y,double z,long now) {
        if(context!=owner)return;
        SOUNDS.observe(id,music,relative,x,y,z,now);
        owner.diagnostics().success(owner.epoch(),"capture_sound_hook_failed");
    }
    public static synchronized void failed(Context owner,String code,Throwable cause) {
        if(context!=owner)return;
        if(code.equals("capture_toast_hook_failed")) { toastConfirmed=false;toasts.clear(); }
        if(code.equals("capture_sound_hook_failed"))SOUNDS.clear();
        if(code.equals("capture_entity_render_hook_failed")) { renderConfirmed=false;rendered.clear(); }
        owner.diagnostics().failure(owner.epoch(),code,cause);
    }
    public static synchronized List<Notice> toasts(long now) {
        if(!toastConfirmed)return null;
        while(!toasts.isEmpty() && now-toasts.getFirst().at()>10_000)toasts.removeFirst();
        return List.copyOf(toasts);
    }
    public static synchronized void rendered(Context owner,int id) {
        if(context!=owner)return;
        renderConfirmed=true;
        if(rendered.size()>=128 && !rendered.containsKey(id))rendered.remove(rendered.keySet().iterator().next());
        rendered.put(id,System.currentTimeMillis());
        owner.diagnostics().success(owner.epoch(),"capture_entity_render_hook_failed");
    }
    public static synchronized List<Integer> rendered(long now) {
        if(!renderConfirmed)return null;
        rendered.values().removeIf(at->now-at>250);return List.copyOf(rendered.keySet());
    }
}

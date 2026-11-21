package com.example.info2ai.http;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.http.handler.*;
import com.example.info2ai.model.Detail;
import com.example.info2ai.model.Snapshot;
import com.example.info2ai.util.JsonUtil;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Routes implements HttpHandler {
    private record Route(String method,HttpHandler handler) { }
    private final Map<String,Route> routes=new HashMap<>();
    private final SnapshotStore store;
    private final Info2AiConfig config;
    private final int port;
    private final long started=System.nanoTime();
    private final LongAdder requests=new LongAdder(),errors=new LongAdder();
    private final List<String> warnings=new CopyOnWriteArrayList<>();
    private long lastErrorLog;

    public Routes(Info2AiConfig config,SnapshotStore store,int port) {
        this.store=store;this.config=config;this.port=port;
        routes.put("/health",new Route("GET",e->Json.write(e,200,health())));
        routes.put("/state",new Route("GET",new StateHandler(config,store)));
        routes.put("/events",new Route("GET",new EventsHandler(store,false)));
        routes.put("/events/history",new Route("GET",new EventsHandler(store,true)));
        routes.put("/sections",new Route("GET",e->{
            requireWorld();JsonObject body=new JsonObject();JsonUtil.putNullable(body,"sections",StateHandler.SECTIONS);
            JsonUtil.putNullable(body,"details",List.of("lite","normal","full"));body.addProperty("schema",1);Json.write(e,200,body);
        }));
        routes.put("/control",new Route("POST",this::control));
        routes.put("/debug/perf",new Route("GET",e->Json.write(e,200,performance())));
        routes.put("/debug/",new Route("GET",DebugPage::write));
        routes.put("/debug",new Route("GET",DebugPage::write));
        routes.put("/screenshot",new Route("GET",new ScreenshotHandler(config,store)));
        routes.put("/director/check",new Route("POST",DirectorHandler::check));
        routes.put("/director/run",new Route("POST",DirectorHandler::run));
        routes.put("/director/status",new Route("GET",DirectorHandler::status));
        routes.put("/director/undo",new Route("POST",DirectorHandler::undo));
        routes.put("/director/actions",new Route("GET",DirectorHandler::actions));
        routes.put("/director/help",new Route("GET",DirectorHandler::help));
        routes.put("/director/lookup",new Route("GET",DirectorHandler::lookup));
        if (Boolean.getBoolean("info2ai.debug")) routes.put("/director/selftest",new Route("GET",DirectorHandler::selftest));
    }
    @Override public void handle(HttpExchange exchange) throws IOException {
        requests.increment();
        exchange.getResponseHeaders().set("X-Info2ai-Schema","1");
        exchange.getResponseHeaders().set("X-Info2ai-Port",Integer.toString(port));
        exchange.getResponseHeaders().set("X-Info2ai-Seq",Long.toString(store.sequence()));
        try {
            Route route=routes.get(exchange.getRequestURI().getPath());
            if(route==null)throw new ApiException(404,"not_found");
            if(!exchange.getRequestMethod().equals(route.method())) {
                exchange.getResponseHeaders().set("Allow",route.method());throw new ApiException(405,"method_not_allowed");
            }
            route.handler().handle(exchange);
        } catch(ApiException failure) {
            JsonObject body=Json.err(failure.code());
            if(failure.code().equals("bad_detail"))JsonUtil.putNullable(body,"allowed",List.of("lite","normal","full"));
            if(failure.code().equals("bad_section")) { JsonUtil.putNullable(body,"allowed",StateHandler.SECTIONS);body.addProperty("name",failure.subject()); }
            if(failure.status()==409)body.addProperty("instance_id",store.instanceId());
            if(failure.resetInSeconds()!=null)body.addProperty("reset_in_s",failure.resetInSeconds());
            Json.write(exchange,failure.status(),body);
        } catch(IOException disconnected) {
            exchange.close();LoggerFactory.getLogger("info2ai.http").debug("HTTP 连接关闭",disconnected);
        } catch(RuntimeException failure) {
            recordFailure("internal_error",failure);
            Json.write(exchange,500,Json.err("internal_error"));
        }
    }
    private JsonObject health() {
        Snapshot snapshot=store.current();
        JsonObject body=new JsonObject();
        body.addProperty("ok",true);body.addProperty("mod_version","0.1.0");body.addProperty("schema",1);
        body.addProperty("mc_version","1.21.1");body.addProperty("in_world",snapshot!=null);
        body.addProperty("degraded",!warnings.isEmpty() || !store.warnings().isEmpty());body.addProperty("port",port);body.addProperty("seq",store.sequence());
        body.addProperty("instance_id",store.instanceId());body.addProperty("mock",config.mock()&&!store.replay());body.addProperty("replay",store.replay());body.addProperty("source_status",store.sourceStatus());
        body.addProperty("uptime_s",(System.nanoTime()-started)/1_000_000_000);
        JsonObject capabilities=snapshot==null || snapshot.capabilities()==null?new JsonObject():snapshot.capabilities().toJson();
        capabilities.addProperty("paused",store.paused());
        capabilities.addProperty("director",Info2AiApi.director().available());
        capabilities.addProperty("hide_hud_supported",false);
        capabilities.addProperty("replay_state",store.replay()&&snapshot!=null);capabilities.addProperty("replay_events",store.replay()&&!store.sourceStatus().equals("unavailable"));
        if(snapshot==null) {
            for(String name:List.of("client_capture","screenshot","chat_write","overlay_write","tts"))capabilities.addProperty(name,false);
        }
        body.add("capabilities",capabilities);
        body.add("static_source",store.staticMetrics());
        List<String> combinedWarnings=new ArrayList<>(warnings);combinedWarnings.addAll(store.warnings());
        JsonUtil.putNullable(body,"warnings",combinedWarnings);return body;
    }
    private JsonObject performance() {
        Snapshot snapshot=store.current();
        JsonObject body=new JsonObject(),events=new JsonObject(),http=new JsonObject();
        var sample=store.perfStats().snapshot();
        JsonObject capture=new JsonObject();capture.addProperty("samples",sample.samples());capture.addProperty("avg_ms",sample.avgMs());capture.addProperty("max_ms",sample.maxMs());
        body.add("capture",capture);
        body.add("static_source",store.staticMetrics());
        body.add("client_capture",perfJson(store.clientPerfStats().snapshot()));
        body.add("screenshot",perfJson(store.screenshotPerfStats().snapshot()));
        body.add("event_engine",perfJson(store.eventPerfStats().snapshot()));
        body.add("signal_engine",perfJson(store.signalPerfStats().snapshot()));
        var director=Info2AiApi.director().executionPerf();
        if(director!=null)body.add("director_run",perfJson(director));
        body.addProperty("snapshot_age_ms",snapshot==null?-1:System.currentTimeMillis()-snapshot.capturedAt());
        events.addProperty("ring_size",store.events().size());events.addProperty("total_emitted",store.totalEvents());events.addProperty("dropped",store.events().dropped());body.add("events",events);
        http.addProperty("requests",requests.sum());http.addProperty("waiters",store.polls().waiterCount());http.addProperty("errors",errors.sum());body.add("http",http);
        if(store.history()!=null)body.add("history_io",store.history().metrics());
        return body;
    }
    private static JsonObject perfJson(com.example.info2ai.capture.merge.PerfStats.Sample sample) {
        JsonObject result=new JsonObject(); result.addProperty("samples",sample.samples());
        result.addProperty("avg_ms",sample.avgMs()); result.addProperty("max_ms",sample.maxMs()); return result;
    }
    private void control(HttpExchange exchange) throws IOException {
        byte[] bytes=exchange.getRequestBody().readNBytes(65537);
        if(bytes.length>65536)throw new ApiException(413,"body_too_large");
        JsonObject request;
        try { request=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch(RuntimeException badJson) { throw new ApiException(400,"invalid_json"); }
        Boolean pause=null, directorEnabled=null;
        boolean clear=false,reload=false;
        Detail detail=null;
        try {
            if(request.has("pause_export"))pause=booleanValue(request,"pause_export");
            if(request.has("director_enabled"))directorEnabled=booleanValue(request,"director_enabled");
            if(request.has("clear_history"))clear=booleanValue(request,"clear_history");
            if(request.has("reload_config"))reload=booleanValue(request,"reload_config");
            if(request.has("detail")) {
                if(!request.get("detail").isJsonPrimitive() || !request.getAsJsonPrimitive("detail").isString())throw new ApiException(400,"bad_control");
                detail=Detail.parse(request.get("detail").getAsString());
            }
        } catch(IllegalArgumentException | IllegalStateException badValue) { throw new ApiException(400,"bad_control"); }
        if(reload) {
            try { config.reload();warnings.remove("config_reload_failed");warnings.remove("mock_mode_requires_restart"); }
            catch(Info2AiConfig.ModeChangeRequiresRestartException restart) {
                if (!warnings.contains("mock_mode_requires_restart")) warnings.add("mock_mode_requires_restart");
                throw new ApiException(409,"mock_mode_requires_restart");
            }
            catch(IOException failure) { recordFailure("config_reload_failed",failure);throw new ApiException(500,"config_reload_failed"); }
        }
        if(pause!=null)store.paused(pause);
        if(directorEnabled!=null)Info2AiApi.director().enabled(directorEnabled);
        if(clear) { store.clearHistory(); }
        if(detail!=null)config.detail(detail);
        JsonObject body=new JsonObject(),state=new JsonObject();body.addProperty("ok",true);
        state.addProperty("pause_export",store.paused());state.addProperty("detail",config.detail().jsonName());
        if(directorEnabled!=null)state.addProperty("director_enabled",config.director().enabled());
        body.add("state",state);Json.write(exchange,200,body);
    }
    private static boolean booleanValue(JsonObject request,String key) {
        if(!request.get(key).isJsonPrimitive() || !request.getAsJsonPrimitive(key).isBoolean())throw new ApiException(400,"bad_control");
        return request.get(key).getAsBoolean();
    }
    private void requireWorld() { if(store.current()==null)throw new ApiException(503,"not_in_world"); }
    private synchronized void recordFailure(String code,Throwable failure) {
        errors.increment();
        if(!warnings.contains(code))warnings.add(code);
        long now=System.nanoTime();
        if(now-lastErrorLog>1_000_000_000L) { lastErrorLog=now;LoggerFactory.getLogger("info2ai.http").error("HTTP 子系统失败: "+code,failure); }
    }
}

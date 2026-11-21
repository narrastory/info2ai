package com.example.info2ai;

import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.http.*;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.google.gson.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class HttpContractTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private HttpServerHost host;
    private SnapshotStore store;
    private Info2AiConfig config;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Before public void start() throws Exception {
        Path dir=temp.newFolder().toPath();
        Files.writeString(dir.resolve("info2ai.json"),"{\"http\":{\"port\":0},\"mock\":true}");
        config=Info2AiConfig.load(dir);
        store=new SnapshotStore(4);
        store.publish(MockSnapshotFactory.create());
        host=HttpServerHost.start(config,store);
    }
    @After public void stop() { if(host!=null)host.close(); if(store!=null)store.close(); com.example.info2ai.api.Info2AiApi.client(com.example.info2ai.api.ClientBridge.NONE); }
    private HttpRequest.Builder req(String path) { return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+host.actualPort()+path)).timeout(Duration.ofSeconds(4)); }
    private HttpResponse<String> get(String path,boolean auth) throws Exception {
        var builder=req(path);
        if(auth)builder.header("Authorization","Bearer "+config.token());
        return client.send(builder.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonObject json(HttpResponse<String> response) { return JsonParser.parseString(response.body()).getAsJsonObject(); }

    @Test public void debugPageShellIsDataFreeAndDataStillRequiresToken() throws Exception {
        for(String path:List.of("/debug/","/debug")) {
            var page=get(path,false);
            assertEquals(200,page.statusCode());
            assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
            assertTrue(page.headers().firstValue("Content-Security-Policy").orElse("").contains("connect-src 'self'"));
            assertEquals("DENY",page.headers().firstValue("X-Frame-Options").orElse(""));
            assertTrue(page.body().contains("info2ai"));
            assertFalse("页面外壳不得包含令牌",page.body().contains(config.token()));
            assertFalse(page.headers().map().containsKey("Access-Control-Allow-Origin"));
        }
        assertEquals(401,get("/debug/perf",false).statusCode());
        assertEquals(401,get("/debug/x",false).statusCode());
        var performance=get("/debug/perf",true);
        assertEquals(200,performance.statusCode());
        assertTrue(json(performance).get("static_source").isJsonObject());
    }
    @Test public void standaloneMockScreenshotIsHonestlyDisabled() throws Exception {
        var response = get("/screenshot", true);
        assertEquals(503, response.statusCode());
        assertEquals("screenshot_disabled", json(response).get("error").getAsString());
    }
    @Test public void gameTextWritebackEndpointIsNotExposed() throws Exception {
        for(String body:List.of("{\"text\":\"removed feature\"}","not-json")) {
            var response=client.send(req("/say").header("Authorization","Bearer "+config.token())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(404,response.statusCode());assertEquals("not_found",json(response).get("error").getAsString());
        }
        store.clearWorld();
        assertEquals(404,get("/say",true).statusCode());
    }
    @Test public void obsoleteSpeechSettingsDoNotResetCaptureConfiguration() throws Exception {
        Path directory=temp.newFolder().toPath();
        Files.writeString(directory.resolve("info2ai.json"),"""
            {"http":{"port":12345,"token":"keep-existing-token"},"capture":{"maxEntities":7},
             "client":{"speechBubble":true,"bubbleDurationMs":-1,"maxBubbleChars":0,
                       "allowServerChat":true,"tts":{"enabled":true},"screenshot":{"maxWidth":640}}}
            """);
        var loaded=Info2AiConfig.load(directory);
        assertEquals(12345,loaded.port());assertEquals("keep-existing-token",loaded.token());assertEquals(7,loaded.maxEntities());
        var settings=JsonParser.parseString(Files.readString(directory.resolve("info2ai.json"))).getAsJsonObject();
        var clientSettings=settings.getAsJsonObject("client");
        assertEquals(640,clientSettings.getAsJsonObject("screenshot").get("maxWidth").getAsInt());
        for(String obsolete:List.of("speechBubble","bubbleDurationMs","maxBubbleChars","allowServerChat","tts"))assertFalse(obsolete,clientSettings.has(obsolete));
    }
    @Test public void removedWritebackCapabilitiesAreFalseInMockAndWithoutWorld() throws Exception {
        for(String route:List.of("/state","/health")) {
            var capabilities=json(get(route,true)).getAsJsonObject("capabilities");
            for(String key:List.of("chat_write","overlay_write","tts"))assertEquals(key,new JsonPrimitive(false),capabilities.get(key));
        }
        store.clearWorld();
        var capabilities=json(get("/health",false)).getAsJsonObject("capabilities");
        for(String key:List.of("chat_write","overlay_write","tts"))assertEquals(key,new JsonPrimitive(false),capabilities.get(key));
    }

    @Test public void screenshotValidatesRequestsAndKeepsCaptureMetadataWhenSnapshotAdvances() throws Exception {
        restartInMode(false);
        var ready = MockSnapshotFactory.create().toJson();
        ready.getAsJsonObject("capabilities").addProperty("screenshot", true);
        store.publish(com.example.info2ai.model.Snapshot.fromJson(ready));
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        com.example.info2ai.api.Info2AiApi.client((width, hide, quality, format) -> {
            calls.incrementAndGet();
            assertEquals(480, width); assertTrue(hide); assertEquals(85, quality); assertEquals("jpg", format);
            store.publish(MockSnapshotFactory.create());
            return new com.example.info2ai.api.ScreenshotResult(new byte[]{1,2,3}, 42, 123456, null, false);
        });
        for (String query : List.of("max_width=159", "max_width=3841", "max_width=no", "quality=0", "quality=101", "hide_hud=2", "format=gif"))
            assertEquals(query, 400, get("/screenshot?" + query, true).statusCode());
        assertEquals(0, calls.get());
        var response = get("/screenshot?max_width=480&hide_hud=1&format=jpg", true);
        assertEquals(200, response.statusCode());
        assertEquals("42", response.headers().firstValue("X-Info2ai-Seq").orElseThrow());
        assertEquals("123456", response.headers().firstValue("X-Info2ai-RealTime").orElseThrow());
        assertEquals("image/png", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("png", response.headers().firstValue("X-Info2ai-Format").orElseThrow());
        assertEquals("false", response.headers().firstValue("X-Info2ai-Hide-Hud-Supported").orElseThrow());
        assertEquals(1, calls.get());
    }

    @Test public void mockCannotCallEvenAnExplicitBridgeWithMisleadingCapability() throws Exception {
        var ready=MockSnapshotFactory.create().toJson(); ready.getAsJsonObject("capabilities").addProperty("screenshot",true);
        store.publish(com.example.info2ai.model.Snapshot.fromJson(ready));
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        com.example.info2ai.api.Info2AiApi.client((w,h,q,f)->{ calls.incrementAndGet(); throw new AssertionError("Mock crossed into framebuffer"); });
        assertEquals("screenshot_disabled",json(get("/screenshot",true)).get("error").getAsString());
        assertEquals(0,calls.get());
    }

    @Test public void screenshotNoClientPauseAndUnloadRemainExplicit() throws Exception {
        restartInMode(false);
        var ready=MockSnapshotFactory.create().toJson(); ready.getAsJsonObject("capabilities").addProperty("screenshot",true);
        store.publish(com.example.info2ai.model.Snapshot.fromJson(ready));
        assertEquals("no_client",json(get("/screenshot",true)).get("error").getAsString());
        store.paused(true); assertEquals("paused",json(get("/screenshot",true)).get("error").getAsString());
        store.clearWorld(); assertEquals("not_in_world",json(get("/screenshot",true)).get("error").getAsString());
        assertFalse(json(get("/health",false)).getAsJsonObject("capabilities").get("client_capture").getAsBoolean());
    }

    @Test public void realFreshRequestWaitsForPureServerQueueAndTimesOutHonestly() throws Exception {
        restartInMode(false);
        store.enableFresh(true);
        long previous=store.current().seq();
        var waiting=client.sendAsync(req("/state?fresh=1&detail=lite").header("Authorization","Bearer "+config.token()).GET().build(),HttpResponse.BodyHandlers.ofString());
        List<com.example.info2ai.capture.merge.FreshRequest> batch=List.of();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(batch.isEmpty() && System.nanoTime()<deadline) { batch=store.takeFreshRequests();Thread.onSpinWait(); }
        assertEquals(1,batch.size());assertFalse(waiting.isDone());
        var accepted=store.publishServer(MockSnapshotFactory.create());store.completeFresh(batch,accepted);
        var response=waiting.get(2,TimeUnit.SECONDS);assertEquals(200,response.statusCode());assertTrue(json(response).get("seq").getAsLong()>previous);
        var timedOut=get("/state?fresh=1",true);assertEquals(504,timedOut.statusCode());assertEquals("fresh_timeout",json(timedOut).get("error").getAsString());
        assertTrue(store.takeFreshRequests().isEmpty());
    }

    @Test public void inFlightFreshCannotReturnSameNamedReplacementWorld() throws Exception {
        restartInMode(false); store.enableFresh(true);
        var waiting=client.sendAsync(req("/state?fresh=1").header("Authorization","Bearer "+config.token()).GET().build(),HttpResponse.BodyHandlers.ofString());
        List<com.example.info2ai.capture.merge.FreshRequest> taken=List.of();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(taken.isEmpty() && System.nanoTime()<deadline) { taken=store.takeFreshRequests();Thread.onSpinWait(); }
        assertEquals(1,taken.size()); long old=store.worldEpoch();
        store.invalidateWorld(old);var rejected=store.publishServer(MockSnapshotFactory.create(),old);assertNull(rejected);
        var replacement=MockSnapshotFactory.create().toJson();replacement.getAsJsonObject("player").addProperty("name","replacement-player");
        store.publishServer(com.example.info2ai.model.Snapshot.fromJson(replacement),store.worldEpoch());
        store.completeFresh(taken,rejected);
        var response=waiting.get(2,TimeUnit.SECONDS);
        assertEquals(503,response.statusCode());assertFalse(response.body().contains("replacement-player"));
    }

    @Test public void freshBodySeqAndTimeUseAcceptedServerSnapshotDespiteLaterClientPublication() throws Exception {
        for(String detail:List.of("full","lite")) {
        restartInMode(false);store.enableFresh(true);
        var waiting=client.sendAsync(req("/state?fresh=1&detail="+detail).header("Authorization","Bearer "+config.token()).GET().build(),HttpResponse.BodyHandlers.ofString());
        List<com.example.info2ai.capture.merge.FreshRequest> taken=List.of();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(taken.isEmpty() && System.nanoTime()<deadline) { taken=store.takeFreshRequests();Thread.onSpinWait(); }
        assertEquals(1,taken.size());
        var server=MockSnapshotFactory.create().toJson();server.getAsJsonObject("capabilities").addProperty("server_capture",true);
        server.getAsJsonObject("player").addProperty("screen","inventory");
        var accepted=store.publishServer(com.example.info2ai.model.Snapshot.fromJson(server));
        var clientSlice=server.deepCopy();clientSlice.getAsJsonObject("capabilities").addProperty("server_capture",false);
        clientSlice.getAsJsonObject("capabilities").addProperty("client_capture",true);clientSlice.getAsJsonObject("player").addProperty("screen","chest");
        store.publishClient(com.example.info2ai.model.Snapshot.fromJson(clientSlice));
        deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(store.current().seq()==accepted.snapshot().seq() && System.nanoTime()<deadline)Thread.sleep(5);
        assertEquals("chest",store.current().player().screen());assertFalse(waiting.isDone());
        store.completeFresh(taken,accepted);
        var response=waiting.get(2,TimeUnit.SECONDS);assertEquals(200,response.statusCode());
        assertEquals("inventory",json(response).getAsJsonObject("player").get("screen").getAsString());
        assertEquals(accepted.snapshot().seq(),json(response).get("seq").getAsLong());
        assertEquals(Long.toString(accepted.snapshot().seq()),response.headers().firstValue("X-Info2ai-Seq").orElseThrow());
        assertEquals(accepted.snapshot().capturedAt(),json(response).get("captured_at").getAsLong());
        assertEquals(Long.toString(accepted.snapshot().capturedAt()),response.headers().firstValue("X-Info2ai-RealTime").orElseThrow());
        if(detail.equals("lite"))assertTrue(response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=3072);
        }
    }

    @Test public void screenshotFailureDegradesHealthAndSuccessfulCaptureRecovers() throws Exception {
        restartInMode(false);var ready=MockSnapshotFactory.create().toJson();ready.getAsJsonObject("capabilities").addProperty("screenshot",true);
        store.publish(com.example.info2ai.model.Snapshot.fromJson(ready));
        var now=new java.util.concurrent.atomic.AtomicLong();var broken=new java.util.concurrent.atomic.AtomicBoolean(true);
        var coordinator=new com.example.info2ai.screenshot.CaptureCoordinator(Runnable::run,()->true,now::get,2000);
        com.example.info2ai.api.Info2AiApi.client((w,h,q,f)->coordinator.capture(()->true,()->{
            if(broken.get())throw new IllegalStateException("encode failure");
            return new com.example.info2ai.api.ScreenshotResult(new byte[]{1},store.current().seq(),123,null,false);
        },store.diagnostics(),store.worldEpoch()));
        assertEquals(503,get("/screenshot",true).statusCode());
        var failed=json(get("/health",false));assertTrue(failed.get("degraded").getAsBoolean());
        assertTrue(failed.getAsJsonArray("warnings").toString().contains("screenshot_failed"));
        broken.set(false);now.set(1_000_000_000L);assertEquals(200,get("/screenshot",true).statusCode());
        var recovered=json(get("/health",false));assertFalse(recovered.getAsJsonArray("warnings").toString().contains("screenshot_failed"));
        assertFalse(recovered.get("degraded").getAsBoolean());
    }

    private void restartInMode(boolean mock) throws Exception {
        host.close(); store.close();
        Path directory = temp.newFolder().toPath();
        Files.writeString(directory.resolve("info2ai.json"), "{\"http\":{\"port\":0},\"mock\":" + mock + "}");
        config = Info2AiConfig.load(directory);
        store = new SnapshotStore(4);
        store.publish(MockSnapshotFactory.create());
        host = HttpServerHost.start(config, store);
    }

    private void editMock(boolean value) throws Exception {
        Path path = config.directory().resolve("info2ai.json");
        JsonObject settings = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        settings.addProperty("mock", value);
        Files.writeString(path, settings.toString());
    }

    @Test public void mockModeCannotReloadToRealAndLeavesFreshAndHealthConsistent() throws Exception {
        editMock(false);
        var response = client.send(req("/control").header("Authorization", "Bearer " + config.token())
            .POST(HttpRequest.BodyPublishers.ofString("{\"reload_config\":true}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(409, response.statusCode());
        assertEquals("mock_mode_requires_restart", json(response).get("error").getAsString());
        assertTrue(config.mock());
        assertTrue(json(get("/health", false)).get("mock").getAsBoolean());
        assertEquals(200, get("/state?fresh=1", true).statusCode());
    }

    @Test public void realModeCannotReloadToMockAndNeverPublishesMockForFresh() throws Exception {
        restartInMode(false);
        editMock(true);
        assertThrows(java.io.IOException.class, config::reload);
        var rejected = client.send(req("/control").header("Authorization", "Bearer " + config.token())
            .POST(HttpRequest.BodyPublishers.ofString("{\"reload_config\":true}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(409, rejected.statusCode());
        assertEquals("mock_mode_requires_restart", json(rejected).get("error").getAsString());
        assertTrue(json(get("/health", false)).getAsJsonArray("warnings").toString().contains("mock_mode_requires_restart"));
        assertFalse(config.mock());
        assertFalse(json(get("/health", false)).get("mock").getAsBoolean());
        long before = store.current().seq();
        assertEquals(503, get("/state?fresh=1", true).statusCode());
        assertEquals(before, store.current().seq());
    }

    @Test public void requestEntityLimitCanExpandPastDefaultEvenWhenConfiguredDefaultIsZero() throws Exception {
        var source = MockSnapshotFactory.create().toJson();
        JsonArray candidates = new JsonArray();
        List<Integer> entities = new ArrayList<>();
        for (int i = 39; i >= 0; i--) entities.add(i);
        for (int i : com.example.info2ai.capture.server.EntityCandidates.retain(entities, Integer::doubleValue)) {
            JsonObject entity = new JsonObject();
            entity.addProperty("type", "minecraft:zombie"); entity.addProperty("id_local", i);
            entity.addProperty("distance", i); candidates.add(entity);
        }
        source.getAsJsonObject("surroundings").add("entities", candidates);
        store.publish(com.example.info2ai.model.Snapshot.fromJson(source));
        assertEquals(16, json(get("/state?detail=normal", true)).getAsJsonObject("surroundings").getAsJsonArray("entities").size());
        assertEquals(32, json(get("/state?detail=normal&max_entities=32", true)).getAsJsonObject("surroundings").getAsJsonArray("entities").size());
        Path path = config.directory().resolve("info2ai.json");
        JsonObject settings = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        settings.getAsJsonObject("capture").addProperty("maxEntities", 0);
        Files.writeString(path, settings.toString()); config.reload();
        assertEquals(0, json(get("/state?detail=normal", true)).getAsJsonObject("surroundings").getAsJsonArray("entities").size());
        assertEquals(32, json(get("/state?detail=normal&max_entities=32", true)).getAsJsonObject("surroundings").getAsJsonArray("entities").size());
    }

    @Test public void damageHookMissingFailedAndRecoveredStatusAreHonestInHealthWarnings() throws Exception {
        var hook = new com.example.info2ai.capture.server.DamageHook(store);
        var missing = json(get("/health", false));
        assertTrue(missing.get("degraded").getAsBoolean());
        assertTrue(missing.getAsJsonArray("warnings").toString().contains("capture_damage_hook_unconfirmed"));
        hook.begin(20, 0);
        hook.finishTick();
        var failed = json(get("/health", false));
        assertTrue(failed.getAsJsonArray("warnings").toString().contains("capture_damage_hook_failed"));
        hook.begin(20, 0);
        hook.finish(18, 0);
        var recovered = json(get("/health", false));
        assertFalse(recovered.get("degraded").getAsBoolean());
        assertFalse(recovered.getAsJsonArray("warnings").toString().contains("capture_damage_hook"));
        hook.stop();
        store.clearWorld();
        hook.start();
        assertTrue(json(get("/health", false)).getAsJsonArray("warnings").toString().contains("capture_damage_hook_unconfirmed"));
    }

    @Test public void perfAndCaptureWarningsAreVisibleWithoutTouchingGameObjects() throws Exception {
        store.perfStats().record(1000);store.perfStats().record(2000);store.warning("capture_humidity_unavailable");
        var perf=json(get("/debug/perf",true)).getAsJsonObject("capture");
        assertEquals(2,perf.get("samples").getAsInt());assertEquals(1.5,perf.get("avg_ms").getAsDouble(),0);assertEquals(2,perf.get("max_ms").getAsDouble(),0);
        var health=json(get("/health",false));assertTrue(health.get("degraded").getAsBoolean());assertTrue(health.getAsJsonArray("warnings").toString().contains("capture_humidity_unavailable"));
    }

    @Test public void healthAndAuthenticationAndIndependentEtags() throws Exception {
        assertEquals(200,get("/health",false).statusCode());
        assertEquals(401,get("/state",false).statusCode());
        var first=get("/state?detail=lite&sections=player",true);
        assertEquals(200,first.statusCode());
        assertFalse(json(first).has("inventory"));
        assertEquals("lite",json(first).get("detail").getAsString());
        String etag=first.headers().firstValue("ETag").orElseThrow();
        var unchanged=client.send(req("/state?detail=lite&sections=player&if_changed=1").header("Authorization","Bearer "+config.token()).header("If-None-Match",etag).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(304,unchanged.statusCode());
        assertEquals("",unchanged.body());
        assertEquals(200,get("/state?detail=lite&sections=player&if_changed=1",true).statusCode());
        assertEquals(400,get("/state?sections=unknown",true).statusCode());
        assertEquals(400,get("/state?max_entities=999",true).statusCode());
    }

    @Test public void eventsHeartbeatWakeAndOverflowAreExplicit() throws Exception {
        long cursor=store.current().seq();
        long started=System.nanoTime();
        var heartbeat=get("/events?since="+cursor+"&wait_ms=120",true);
        assertEquals(200,heartbeat.statusCode());
        assertTrue(json(heartbeat).get("heartbeat").getAsBoolean());
        assertTrue(Duration.ofNanos(System.nanoTime()-started).toMillis()>=90);
        var waiting=client.sendAsync(req("/events?since="+cursor+"&wait_ms=3000").header("Authorization","Bearer "+config.token()).GET().build(),HttpResponse.BodyHandlers.ofString());
        awaitWaiters(1);
        store.emit("death","critical","测试死亡");
        var event=waiting.get(2,TimeUnit.SECONDS);
        assertEquals(json(event).get("seq").getAsString(),event.headers().firstValue("X-Info2ai-Seq").orElseThrow());
        assertEquals("death",json(event).getAsJsonArray("events").get(0).getAsJsonObject().get("kind").getAsString());
        for(int i=0;i<6;i++)store.emit("test","info","event "+i);
        var lost=json(get("/events?since="+cursor,true));
        assertTrue(lost.get("lost").getAsBoolean());
        assertTrue(lost.has("instance_id") && lost.has("oldest_seq"));
    }

    @Test public void sixteenLongPollsDoNotBlockHealthAndSeventeenthIsRejected() throws Exception {
        List<CompletableFuture<HttpResponse<String>>> requests=new ArrayList<>();
        long cursor=store.current().seq();
        try {
            for(int i=0;i<16;i++)requests.add(client.sendAsync(req("/events?since="+cursor+"&wait_ms=3000").header("Authorization","Bearer "+config.token()).GET().build(),HttpResponse.BodyHandlers.ofString()));
            awaitWaiters(16);
            assertEquals(200,get("/health",false).statusCode());
            assertEquals(200,get("/state",true).statusCode());
            assertEquals(429,get("/events?since="+cursor+"&wait_ms=3000",true).statusCode());
            store.emit("wake","info","wake");
            for(var pending:requests)assertEquals(200,pending.get(2,TimeUnit.SECONDS).statusCode());
        } finally { store.polls().signal(); for(var pending:requests)pending.cancel(true); }
    }

    @Test public void pauseUnknownMethodsAndRequestBodyLimits() throws Exception {
        var pause=client.send(req("/control").header("Authorization","Bearer "+config.token()).POST(HttpRequest.BodyPublishers.ofString("{\"pause_export\":true}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,pause.statusCode());
        assertTrue(json(get("/health",false)).getAsJsonObject("capabilities").get("paused").getAsBoolean());
        assertEquals(503,get("/state",true).statusCode());
        assertEquals("paused",json(get("/state",true)).get("error").getAsString());
        assertEquals(405,get("/control",true).statusCode());
        var tooLarge=client.send(req("/control").header("Authorization","Bearer "+config.token()).POST(HttpRequest.BodyPublishers.ofString(" ".repeat(65537))).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(413,tooLarge.statusCode());
    }

    @Test public void emptyWorldReportsLifecycleWithoutBusyLongPoll() throws Exception {
        store.clearWorld();
        assertEquals(503,get("/state",true).statusCode());
        var events=json(get("/events?wait_ms=2000",true));
        assertFalse(events.get("in_world").getAsBoolean());
        assertTrue(events.has("instance_id"));
    }

    @Test public void configurationKeepsTokenAndBacksUpCorruption() throws Exception {
        assertEquals(64,config.token().length());
        assertEquals(config.token(),Files.readString(config.directory().resolve("info2ai.token")).trim());
        assertEquals(config.token(),Info2AiConfig.load(config.directory()).token());
        Files.writeString(config.directory().resolve("info2ai.json"),"invalid-json");
        Info2AiConfig repaired=Info2AiConfig.load(config.directory());
        assertTrue(Files.exists(config.directory().resolve("info2ai.json.bad")));
        assertEquals(64,repaired.token().length());
    }

    @Test public void configurationRepairsInvalidTypedFieldsAndFillsDefaults() throws Exception {
        Path directory=temp.newFolder().toPath();
        Files.writeString(directory.resolve("info2ai.json"),"{\"http\":{\"port\":\"oops\"}}");
        Info2AiConfig repaired=Info2AiConfig.load(directory);
        assertEquals(27948,repaired.port());
        assertTrue(Files.exists(directory.resolve("info2ai.json.bad")));
        JsonObject persisted=JsonParser.parseString(Files.readString(directory.resolve("info2ai.json"))).getAsJsonObject();
        assertEquals(512,persisted.getAsJsonObject("pipeline").get("eventRing").getAsInt());
        assertTrue(persisted.getAsJsonObject("client").getAsJsonObject("screenshot").get("enabled").getAsBoolean());
    }

    @Test public void hashOnlySelectionAndConfiguredEntityLimitWork() throws Exception {
        var original=json(get("/state?sections=hashes",true));
        assertTrue(original.getAsJsonObject("hashes").has("player"));
        JsonObject sample=MockSnapshotFactory.create().toJson();
        sample.getAsJsonObject("player").addProperty("health",7);
        JsonArray entities=new JsonArray();
        for(int i=0;i<3;i++){JsonObject entity=new JsonObject();entity.addProperty("type","minecraft:zombie");entities.add(entity);}
        sample.getAsJsonObject("surroundings").add("entities",entities);
        store.publish(com.example.info2ai.model.Snapshot.fromJson(sample));
        var changed=json(get("/state?sections=hashes",true));
        assertNotEquals(original.getAsJsonObject("hashes").get("player"),changed.getAsJsonObject("hashes").get("player"));
    }

    @Test public void configuredEntityLimitIsUsedWithoutQueryOverride() throws Exception {
        JsonObject sample=MockSnapshotFactory.create().toJson();
        JsonArray entities=new JsonArray();
        for(int i=0;i<3;i++){JsonObject entity=new JsonObject();entity.addProperty("type","minecraft:zombie");entities.add(entity);}
        sample.getAsJsonObject("surroundings").add("entities",entities);
        store.publish(com.example.info2ai.model.Snapshot.fromJson(sample));
        JsonObject settings=JsonParser.parseString(Files.readString(config.directory().resolve("info2ai.json"))).getAsJsonObject();
        settings.getAsJsonObject("capture").addProperty("maxEntities",0);
        Files.writeString(config.directory().resolve("info2ai.json"),settings.toString());config.reload();
        assertEquals(0,json(get("/state",true)).getAsJsonObject("surroundings").getAsJsonArray("entities").size());
    }

    @Test public void invalidControlDoesNotPartiallyPauseAndReloadIoReturnsJson() throws Exception {
        var invalid=client.send(req("/control").header("Authorization","Bearer "+config.token()).POST(HttpRequest.BodyPublishers.ofString("{\"pause_export\":true,\"detail\":null}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(400,invalid.statusCode());assertFalse(store.paused());
    }

    @Test public void configurationReloadIoFailureReturnsJson() throws Exception {
        Path file=config.directory().resolve("info2ai.json");
        Files.delete(file);Files.createDirectory(file);
        var failure=client.send(req("/control").header("Authorization","Bearer "+config.token()).POST(HttpRequest.BodyPublishers.ofString("{\"reload_config\":true}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(500,failure.statusCode());assertEquals("config_reload_failed",json(failure).get("error").getAsString());
        assertTrue(json(get("/health",false)).get("degraded").getAsBoolean());
    }

    @Test public void explicitSectionsDoNotReintroduceHashes() throws Exception {
        assertFalse(json(get("/state?sections=player",true)).has("hashes"));
    }
    @Test public void authenticationErrorsHaveProtocolHeaders() throws Exception {
        var response=get("/state",false);
        assertTrue(response.headers().firstValue("X-Info2ai-Schema").isPresent());
        assertTrue(response.headers().firstValue("X-Info2ai-Port").isPresent());
        assertTrue(response.headers().firstValue("X-Info2ai-Seq").isPresent());
    }
    @Test public void badSectionIdentifiesTheInvalidName() throws Exception {
        assertEquals("unknown",json(get("/state?sections=unknown",true)).get("name").getAsString());
    }

    @Test public void occupiedPortFallsBackAndHealthReportsActualListener() throws Exception {
        java.net.ServerSocket occupied=null;
        for(int port=40000;port<40100 && occupied==null;port++) {
            java.net.ServerSocket candidate=new java.net.ServerSocket();
            try { candidate.bind(new java.net.InetSocketAddress("127.0.0.1",port));occupied=candidate; }
            catch(java.net.BindException inUse) { candidate.close(); }
        }
        assertNotNull("未找到测试端口",occupied);
        try(var reserved=occupied) {
            Path directory=temp.newFolder().toPath();
            Files.writeString(directory.resolve("info2ai.json"),"{\"http\":{\"port\":"+reserved.getLocalPort()+"}}");
            Info2AiConfig second=Info2AiConfig.load(directory);
            try(HttpServerHost fallback=HttpServerHost.start(second,store)) {
                assertTrue(fallback.actualPort()>reserved.getLocalPort());
                assertTrue(fallback.actualPort()<=reserved.getLocalPort()+10);
                var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+fallback.actualPort()+"/health")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(200,response.statusCode());
                assertEquals(fallback.actualPort(),json(response).get("port").getAsInt());
            }
        }
    }

    private void awaitWaiters(int expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(store.polls().waiterCount()<expected && System.nanoTime()<deadline)Thread.sleep(5);
        assertEquals(expected,store.polls().waiterCount());
    }
}

package com.example.info2ai.director;

import com.example.info2ai.api.DirectorPort;
import com.example.info2ai.api.DirectorRequests.*;
import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.director.policy.PolicyDecision;
import com.example.info2ai.http.ApiException;
import com.example.info2ai.http.HttpServerHost;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

import static org.junit.Assert.*;

public class DirectorHttpTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void routesRequireBearerAndPreserveDomainAndTransportStatuses() throws Exception {
        var folder = temporary.newFolder().toPath();
        Files.writeString(folder.resolve("info2ai.json"), "{\"http\":{\"port\":0}}");
        var config = Info2AiConfig.load(folder);
        try (var store = new SnapshotStore(); var host = HttpServerHost.start(config, store);
             var client = HttpClient.newHttpClient()) {
            assertEquals(401, post(client, host, "/director/check", null, "{}").statusCode());
            var unavailable = post(client, host, "/director/check", config.token(), "{\"command\":\"say hi\"}");
            assertEquals(503, unavailable.statusCode());
            assertEquals("not_in_world", JsonParser.parseString(unavailable.body()).getAsJsonObject().get("error").getAsString());
            Info2AiApi.director(new TestDirector() {
                @Override public CheckResult check(String command) {
                    assertEquals("/say hi", command);
                    return new CheckResult(CheckStatus.OK, null, PolicyDecision.allow(), List.of("say"), null);
                }

                @Override public RunResult run(RunRequest request) {
                    assertEquals(Visibility.SIGNED, request.visibility());
                    if (request.command().equals("say busy")) throw new ApiException(429, "rate_limited", 17);
                    return new RunResult(RunStatus.REJECTED, null, false, 0, 0, List.of(), List.of(), null,
                        PolicyDecision.deny("P-ROOT", "不能传送任何实体"), GuardResult.EMPTY, Changes.EMPTY,
                        List.of(), false, new RateStatus(10, 0), null);
                }
            });
            var check = post(client, host, "/director/check", config.token(), "{\"command\":\"/say hi\"}");
            assertEquals(200, check.statusCode());
            assertEquals("ok", JsonParser.parseString(check.body()).getAsJsonObject().get("status").getAsString());
            var rejected = post(client, host, "/director/run", config.token(), "{\"command\":\"tp @p 0 64 0\",\"intent\":\"测试\"}");
            assertEquals(200, rejected.statusCode());
            var body = JsonParser.parseString(rejected.body()).getAsJsonObject();
            assertEquals("rejected", body.get("status").getAsString());
            assertTrue(body.get("action_id").isJsonNull());
            assertEquals(400, post(client, host, "/director/run", config.token(),
                "{\"command\":\"say hi\",\"intent\":123}").statusCode());
            var limited = post(client, host, "/director/run", config.token(), "{\"command\":\"say busy\",\"intent\":\"测试\"}");
            assertEquals(429, limited.statusCode());
            assertEquals(17, JsonParser.parseString(limited.body()).getAsJsonObject().get("reset_in_s").getAsInt());
        } finally {
            Info2AiApi.director(DirectorPort.NONE);
        }
    }

    private static HttpResponse<String> post(HttpClient client, HttpServerHost host, String path, String token,
                                             String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + host.actualPort() + path))
            .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private abstract static class TestDirector implements DirectorPort {
        @Override public boolean available() { return true; }
        @Override public UndoResult undo(UndoRequest request) { throw new AssertionError(); }
        @Override public HelpResult help(String path, String topic) { throw new AssertionError(); }
        @Override public LookupResult lookup(String kind, String query, int limit) { throw new AssertionError(); }
        @Override public List<ActionSummary> actions(int limit) { throw new AssertionError(); }
        @Override public StatusResult status() { throw new AssertionError(); }
        @Override public void enabled(boolean on) { throw new AssertionError(); }
    }
}

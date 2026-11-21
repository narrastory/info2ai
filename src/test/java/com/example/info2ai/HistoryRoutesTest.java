package com.example.info2ai;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.config.Info2AiConfig;
import com.example.info2ai.http.*;
import com.google.gson.*;
import org.junit.Test;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import static org.junit.Assert.*;

public class HistoryRoutesTest {
    @Test public void historyHas2048SlotsAndPerformanceCountsOnlyEvents() throws Exception {
        Path dir=Files.createTempDirectory(Path.of("build"),"history-http-");
        Files.writeString(dir.resolve("info2ai.json"),"{\"http\":{\"port\":0}}");
        var config=Info2AiConfig.load(dir);
        try(var store=new SnapshotStore();var host=HttpServerHost.start(config,store)) {
            store.publish(MockSnapshotFactory.create());
            for(int i=0;i<2200;i++)store.emit("test","info","test");
            var history=get(host,config,"/events/history?limit=2048");
            assertEquals(2048,history.getAsJsonArray("events").size());
            assertTrue(history.get("lost").getAsBoolean());
            assertFalse(history.get("heartbeat").getAsBoolean());
            assertEquals(512,store.events().size());
            assertEquals(2200,get(host,config,"/debug/perf").getAsJsonObject("events").get("total_emitted").getAsLong());
            store.clearWorld();
            assertFalse(get(host,config,"/events/history").get("in_world").getAsBoolean());
        }
    }
    static JsonObject get(HttpServerHost host,Info2AiConfig config,String path) throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+host.actualPort()+path))
            .header("Authorization","Bearer "+config.token()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}

package com.example.info2ai.config;

import com.example.info2ai.director.DirectorSettings;
import com.example.info2ai.model.Detail;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.HexFormat;

/** 配置属于启动/HTTP 生命周期，禁止从 tick 中读写文件。 */
public final class Info2AiConfig {
    private static final Logger LOG=LoggerFactory.getLogger("info2ai.config");
    private static volatile Info2AiConfig current;
    private final Path directory;
    private final String recordPath=System.getProperty("info2ai.record"),replayPath=System.getProperty("info2ai.replay");
    public String recordPath() {return recordPath;}
    public String replayPath() {return replayPath;}
    private volatile Settings settings;
    private volatile Detail detailOverride;
    private record Settings(int port,String token,boolean mock,Detail detail,int maxEntities,JsonObject json,
                            com.example.info2ai.staticworld.WorldgenSettings worldgen, DirectorSettings director) { }

    private Info2AiConfig(Path directory) { this.directory=directory.toAbsolutePath().normalize(); }
    public static Info2AiConfig load(Path directory) throws IOException {
        Info2AiConfig config=new Info2AiConfig(directory);
        config.reload();
        current=config;
        return config;
    }
    public static Info2AiConfig get() {
        if(current==null)throw new IllegalStateException("配置尚未加载");
        return current;
    }
    public static final class ModeChangeRequiresRestartException extends IOException {
        public ModeChangeRequiresRestartException() {
            super("数据源和录制模式在启动时固定，切换模式需要重启游戏或服务。");
        }
    }

    public synchronized void reload() throws IOException {
        Files.createDirectories(directory);
        Path file=directory.resolve("info2ai.json");
        JsonObject json=defaults();
        if(Files.exists(file)) {
            try { merge(json,JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject()); }
            catch(RuntimeException failure) {
                Files.move(file,directory.resolve("info2ai.json.bad"),StandardCopyOption.REPLACE_EXISTING);
                LOG.warn("配置损坏，已备份并使用默认值",failure);
            }
        }
        Settings next;
        try { next=parse(json); }
        catch(RuntimeException failure) {
            if(Files.exists(file))Files.move(file,directory.resolve("info2ai.json.bad"),StandardCopyOption.REPLACE_EXISTING);
            LOG.warn("配置字段无效，已备份并使用默认值",failure);
            next=parse(defaults());
        }
        if (settings != null && (settings.mock() != next.mock()||!java.util.Objects.equals(recordPath,System.getProperty("info2ai.record"))||!java.util.Objects.equals(replayPath,System.getProperty("info2ai.replay")))) {
            throw new ModeChangeRequiresRestartException();
        }
        writeAtomic(file,new GsonBuilder().setPrettyPrinting().create().toJson(next.json())+"\n");
        writeAtomic(directory.resolve("info2ai.token"),next.token()+"\n");
        settings=next;
        detailOverride=null;
    }
    private static Settings parse(JsonObject json) {
            JsonObject http=json.has("http")?json.getAsJsonObject("http"):new JsonObject();
            long configuredPort=http.has("port")?http.get("port").getAsLong():27948;
            if(configuredPort<0 || configuredPort>65525)throw new IllegalArgumentException("端口越界");
            int port=(int)configuredPort;
            String token=http.has("token")?http.get("token").getAsString():"";
            if(token.isBlank()) {
                if(http.has("tokenAuto") && !http.get("tokenAuto").getAsBoolean())throw new IllegalArgumentException("禁用自动令牌时必须配置 token");
                byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
                token=HexFormat.of().formatHex(bytes);
            }
            http.addProperty("host","127.0.0.1");
            http.addProperty("port",port);http.addProperty("token",token);
            if(!http.has("tokenAuto"))http.addProperty("tokenAuto",true);
            json.add("http",http);
            JsonObject capture=json.has("capture")?json.getAsJsonObject("capture"):new JsonObject();
            Detail detail=capture.has("detail")?Detail.parse(capture.get("detail").getAsString()):Detail.NORMAL;
            capture.addProperty("detail",detail.jsonName());
            if(!capture.has("intervalTicks"))capture.addProperty("intervalTicks",4);
            if(!capture.has("maxEntities"))capture.addProperty("maxEntities",16);
            long maxEntities=capture.get("maxEntities").getAsLong();
            if(maxEntities<0 || maxEntities>256)throw new IllegalArgumentException("maxEntities 越界");
            json.add("capture",capture);
            boolean mock=Boolean.getBoolean("info2ai.mock") || (json.has("mock") && json.get("mock").getAsBoolean());
            var worldgen=json.getAsJsonObject("worldgen");
            boolean removedLegacyFields=false;
            for(String key:java.util.List.of("port","url","autoStart","timeoutMs","migratedPrototypePort")) {
                removedLegacyFields |= worldgen.remove(key)!=null;
            }
            if(removedLegacyFields)LOG.warn("worldgen.port/url/autoStart/timeoutMs 已不再使用（静态世界改为模组内计算）");
            long radius=worldgen.get("radius").getAsLong();
            if(radius<1||radius>20000)
                throw new IllegalArgumentException("worldgen bounds invalid");
            var staticSettings=new com.example.info2ai.staticworld.WorldgenSettings(worldgen.get("enabled").getAsBoolean(),
                (int)radius);
            // 移除已取消的界面输出配置；旧字段不能使其他有效配置被重置。
            var client=json.getAsJsonObject("client");
            for(String obsolete:java.util.List.of("speechBubble","bubbleDurationMs","maxBubbleChars","allowServerChat","tts"))client.remove(obsolete);
            DirectorSettings director = DirectorSettings.read(json);
            return new Settings(port,token,mock,detail,(int)maxEntities,json.deepCopy(),staticSettings,director);
    }
    private static JsonObject defaults() {
        return JsonParser.parseString("""
            {"http":{"host":"127.0.0.1","port":27948,"tokenAuto":true,"token":""},
             "capture":{"intervalTicks":4,"detail":"normal","maxEntities":16},
             "pipeline":{"historyBuffer":2048,"eventRing":512,"persistHistory":true},
             "worldgen":{"enabled":true,"radius":2000},
             "client":{"screenshot":{"enabled":true,"maxWidth":960,"maxFps":1}}}
            """).getAsJsonObject();
    }
    private static void merge(JsonObject target,JsonObject source) {
        source.entrySet().forEach(entry->{
            if(entry.getValue().isJsonObject() && target.has(entry.getKey()) && target.get(entry.getKey()).isJsonObject())
                merge(target.getAsJsonObject(entry.getKey()),entry.getValue().getAsJsonObject());
            else target.add(entry.getKey(),entry.getValue().deepCopy());
        });
    }
    private static void writeAtomic(Path target,String text) throws IOException {
        Path temporary=Files.createTempFile(target.getParent(),"info2ai-",".tmp");
        try {
            Files.writeString(temporary,text,StandardCharsets.UTF_8);
            try { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException unavailable) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public int port() { return settings.port(); }
    public String token() { return settings.token(); }
    public boolean mock() { return settings.mock(); }
    public Detail detail() { return detailOverride==null?settings.detail():detailOverride; }
    public void detail(Detail value) { detailOverride=value; }
    public int maxEntities() { return settings.maxEntities(); }
    public com.example.info2ai.staticworld.WorldgenSettings worldgen() {
        return settings.worldgen();
    }
    public DirectorSettings director() { return settings.director(); }
    public synchronized void directorEnabled(boolean enabled) throws IOException {
        Settings previous = settings;
        JsonObject json = previous.json().deepCopy();
        json.getAsJsonObject("director").addProperty("enabled", enabled);
        var director = DirectorSettings.read(json);
        writeAtomic(directory.resolve("info2ai.json"), new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
        settings = new Settings(previous.port(), previous.token(), previous.mock(), previous.detail(), previous.maxEntities(),
            json, previous.worldgen(), director);
    }
    public Path directory() { return directory; }
}

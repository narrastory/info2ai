package com.example.info2ai.util;

import com.example.info2ai.model.*;
import com.google.gson.*;
import java.util.*;
import java.util.function.Function;

/** 显式值编码，不反射遍历对象字段。 */
public final class JsonUtil {
    private JsonUtil() { }
    public static double round1(double value) { return round(value, 10); }
    public static double round2(double value) { return round(value, 100); }
    public static double round3(double value) { return round(value, 1000); }
    private static double round(double value, double scale) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("JSON 数值必须有限");
        if (Math.abs(value) > Long.MAX_VALUE / scale) return value;
        return Math.round(value * scale) / scale;
    }
    public static <T> List<T> immutableSlots(List<T> source) {
        return source == null ? null : Collections.unmodifiableList(new ArrayList<>(source));
    }
    public static List<List<Double>> immutablePositions(List<List<Double>> source) {
        return source == null ? null : immutableSlots(source.stream().map(JsonUtil::immutableSlots).toList());
    }
    public static void putNullable(JsonObject parent, String key, Object value) { put(parent, key, value, Detail.FULL); }
    public static void put(JsonObject parent, String key, Object value, Detail detail) { parent.add(key, encode(value, detail)); }
    public static JsonElement encode(Object value, Detail detail) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonModel model) return model.toJson(detail);
        if (value instanceof String text) return new JsonPrimitive(text);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Number number) {
            if ((number instanceof Double || number instanceof Float) && !Double.isFinite(number.doubleValue()))
                throw new IllegalArgumentException("JSON 数值必须有限");
            return new JsonPrimitive(number);
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            list.forEach(element -> array.add(encode(element, detail)));
            return array;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject result = new JsonObject();
            map.forEach((key, element) -> result.add((String) key, encode(element, detail)));
            return result;
        }
        throw new IllegalArgumentException("未注册的 JSON 类型: " + value.getClass().getName());
    }
    public static JsonElement get(JsonObject object, String key) { return object == null ? null : object.get(key); }
    public static boolean absent(JsonElement value) { return value == null || value.isJsonNull(); }
    public static JsonObject object(JsonObject parent, String key) { var value=get(parent,key); return absent(value)?null:value.getAsJsonObject(); }
    public static String string(JsonObject parent, String key) { var value=get(parent,key); return absent(value)?null:value.getAsString(); }
    public static Integer integer(JsonObject parent, String key) { var value=get(parent,key); return absent(value)?null:value.getAsInt(); }
    public static Long longNumber(JsonObject parent, String key) { var value=get(parent,key); return absent(value)?null:value.getAsLong(); }
    public static Double number(JsonObject parent, String key) { var value=get(parent,key); return absent(value)?null:value.getAsDouble(); }
    public static Boolean bool(JsonObject parent, String key) { var value=get(parent,key); return absent(value)?null:value.getAsBoolean(); }
    public static <T> List<T> list(JsonObject parent, String key, Function<JsonElement,T> parser) {
        var value=get(parent,key);
        if (absent(value)) return null;
        List<T> result=new ArrayList<>();
        for (JsonElement element:value.getAsJsonArray()) result.add(element.isJsonNull()?null:parser.apply(element));
        return immutableSlots(result);
    }
    public static List<String> strings(JsonObject p,String k) { return list(p,k,JsonElement::getAsString); }
    public static List<Double> doubles(JsonObject p,String k) { return list(p,k,JsonElement::getAsDouble); }
    public static List<List<Double>> positions(JsonObject p,String k) {
        return list(p,k,e->{ List<Double> row=new ArrayList<>(); e.getAsJsonArray().forEach(v->row.add(v.getAsDouble())); return immutableSlots(row); });
    }
    public static Map<String,String> stringMap(JsonObject p,String k) {
        JsonObject object=object(p,k);
        if (object==null) return null;
        Map<String,String> result=new LinkedHashMap<>();
        object.entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsString()));
        return Collections.unmodifiableMap(result);
    }
}

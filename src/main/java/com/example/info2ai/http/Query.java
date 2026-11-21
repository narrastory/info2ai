package com.example.info2ai.http;

import com.sun.net.httpserver.HttpExchange;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Query {
    private Query() { }
    public static Map<String,String> parse(HttpExchange exchange) {
        Map<String,String> values=new LinkedHashMap<>();
        String query=exchange.getRequestURI().getRawQuery();
        if(query==null)return values;
        try {
            for(String pair:query.split("&")) {
                String[] parts=pair.split("=",2);
                values.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),parts.length==1?"":URLDecoder.decode(parts[1],StandardCharsets.UTF_8));
            }
        } catch(IllegalArgumentException error) { throw new ApiException(400,"bad_query"); }
        return values;
    }
    public static long number(Map<String,String> query,String key,long fallback,long min,long max) {
        try {
            long value=query.containsKey(key)?Long.parseLong(query.get(key)):fallback;
            if(value<min || value>max)throw new NumberFormatException();
            return value;
        } catch(NumberFormatException error) { throw new ApiException(400,"bad_"+key); }
    }
    public static Set<String> csv(String value) {
        if(value==null || value.isBlank())return Set.of();
        return new LinkedHashSet<>(Arrays.stream(value.split(",")).map(String::trim).toList());
    }
}

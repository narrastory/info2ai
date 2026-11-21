package com.example.info2ai.model;

import java.util.Locale;

public enum Detail {
    LITE, NORMAL, FULL;

    public String jsonName() { return name().toLowerCase(Locale.ROOT); }

    public static Detail parse(String value) {
        if (value == null) throw new IllegalArgumentException("缺少 detail");
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("无效详细度: " + value, error); }
    }
}

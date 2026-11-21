package com.example.info2ai.model;

import com.google.gson.JsonObject;
import java.util.Objects;

/** 可扩展事实的不可变边界；私有副本从不向调用方暴露。 */
public final class Facts implements JsonModel {
    private final JsonObject value;

    private Facts(JsonObject value) { this.value = value.deepCopy(); }
    public static Facts fromJson(JsonObject value) { return value == null ? null : new Facts(value); }
    @Override public JsonObject toJson(Detail detail) { return value.deepCopy(); }
    @Override public boolean equals(Object other) { return other instanceof Facts f && value.equals(f.value); }
    @Override public int hashCode() { return Objects.hash(value); }
}

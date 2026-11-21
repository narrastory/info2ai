package com.example.info2ai.model;

import com.google.gson.JsonObject;

public interface JsonModel {
    JsonObject toJson(Detail detail);
    default JsonObject toJson() { return toJson(Detail.FULL); }
    default void toJson(JsonObject parent, String key) { parent.add(key, toJson()); }
}

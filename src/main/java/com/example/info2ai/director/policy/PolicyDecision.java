package com.example.info2ai.director.policy;

import com.example.info2ai.model.Detail;
import com.example.info2ai.model.JsonModel;
import com.example.info2ai.util.JsonUtil;
import com.google.gson.JsonObject;

public record PolicyDecision(boolean allowed, String rule, String reason) implements JsonModel {
    public static PolicyDecision allow() {
        return new PolicyDecision(true, null, null);
    }

    public static PolicyDecision deny(String rule, String reason) {
        return new PolicyDecision(false, rule, reason);
    }

    @Override
    public JsonObject toJson(Detail detail) {
        JsonObject result = new JsonObject();
        result.addProperty("allowed", allowed);
        JsonUtil.putNullable(result, "rule", rule);
        JsonUtil.putNullable(result, "reason", reason);
        return result;
    }
}

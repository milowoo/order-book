package com.orderbook.core;

import java.util.LinkedHashMap;
import java.util.Map;

public class StrategyContext {
    public final static String CONTEXT_KEY = "context";
    public final static String CONTEXT_GET_KEY = "context.get";
    public final static String CONTEXT_PUT_KEY = "context.put";

    private final Map<String, String> contextSets = new LinkedHashMap<>(4);

    public StrategyContext put(String key, String value) {
        contextSets.put(key, value != null ? value : "null");
        return this;
    }

    public String get(String key) {
        return contextSets.getOrDefault(key, null);
    }
}
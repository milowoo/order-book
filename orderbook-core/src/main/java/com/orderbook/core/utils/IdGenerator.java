package com.orderbook.core.utils;

public class IdGenerator {
    public static String snakeFlowId() {
        return String.valueOf(SnowflakeGenerator.getInstance().nextId());
    }
}
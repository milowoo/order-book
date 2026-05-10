package com.orderbook.core.constants;

public interface RedisKeyConstant {

    String BASE_KEY = "OM:";
    String FILL_KEY = BASE_KEY + "FILL:%s";
}
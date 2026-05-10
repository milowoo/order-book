package com.orderbook.core.store;

import com.google.common.collect.Maps;
import com.orderbook.cmd.ExchangeCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
public class PriceStore {

    private static final Map<String, BigDecimal> prices = Maps.newConcurrentMap();
    private static final Map<String, Long> lastTimes = Maps.newConcurrentMap();

    private static String getKey(String exchangeName, String symbolId) {
        return exchangeName + "_" + symbolId;
    }

    public void setPrice(ExchangeCode exchangeCode, String symbol, BigDecimal price) {
        String key = getKey(exchangeCode.name(), symbol);
        prices.put(key, price);
    }

    public BigDecimal getLastPrice(ExchangeCode exchangeCode, String symbol) {
        String key = getKey(exchangeCode.name(), symbol);
        if (prices.containsKey(key)) {
            return prices.get(key);
        }
        return null;
    }

    public void setLastTime(ExchangeCode exchangeCode, String symbol, Long updateTime) {
        String key = getKey(exchangeCode.name(), symbol);
        lastTimes.put(key, updateTime);
    }

    public Long getLastTime(ExchangeCode exchangeCode, String symbol) {
        String key = getKey(exchangeCode.name(), symbol);
        if (lastTimes.containsKey(key)) {
            return lastTimes.get(key);
        }
        return 0L;
    }
}
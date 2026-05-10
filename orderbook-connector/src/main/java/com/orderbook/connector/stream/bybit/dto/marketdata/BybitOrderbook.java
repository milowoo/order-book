package com.orderbook.connector.stream.bybit.dto.marketdata;

import lombok.Getter;
import java.beans.ConstructorProperties;

@Getter
public class BybitOrderbook {
    private final String topic;
    private final String dataType;
    private final String ts;
    private final BybitOrderbookData data;

    @ConstructorProperties({"topic", "type", "ts", "data"})
    public BybitOrderbook(String topic, String dataType, String ts, BybitOrderbookData data) {
        this.topic = topic;
        this.dataType = dataType;
        this.ts = ts;
        this.data = data;
    }
}
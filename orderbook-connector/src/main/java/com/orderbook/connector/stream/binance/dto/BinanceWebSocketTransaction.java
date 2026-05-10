package com.orderbook.connector.stream.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BinanceWebSocketTransaction {

    private final String eventType;
    private final String eventTime;
    private final String symbol;

    public BinanceWebSocketTransaction(
            @JsonProperty("e") String eventType,
            @JsonProperty("E") String eventTime,
            @JsonProperty("s") String symbol) {
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.symbol = symbol;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventTime() {
        return eventTime;
    }

    public String getSymbol() {
        return symbol;
    }
}

package org.knowm.xchange.binance.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.binance.dto.BinanceException;

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

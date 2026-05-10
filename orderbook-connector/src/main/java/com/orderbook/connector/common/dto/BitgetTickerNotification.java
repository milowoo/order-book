package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class BitgetTickerNotification extends BitgetWsNotification<BitgetTickerNotification.TickerData> {

    @Data
    @Builder
    @Jacksonized
    public static class TickerData {
        @JsonProperty("instId")
        private String instrument;

        @JsonProperty("lastPr")
        private BigDecimal lastPrice;

        @JsonProperty("open24h")
        private BigDecimal open24h;

        @JsonProperty("high24h")
        private BigDecimal high24h;

        @JsonProperty("low24h")
        private BigDecimal low24h;

        @JsonProperty("change24h")
        private BigDecimal change24h;

        @JsonProperty("bidPr")
        private BigDecimal bestBidPrice;

        @JsonProperty("bidSz")
        private BigDecimal bestBidSize;

        @JsonProperty("askPr")
        private BigDecimal bestAskPrice;

        @JsonProperty("askSz")
        private BigDecimal bestAskSize;

        @JsonProperty("quoteVolume")
        private BigDecimal quoteVolume24h;

        @JsonProperty("baseVolume")
        private BigDecimal assetVolume24h;

        @JsonProperty("openUtc")
        private BigDecimal openUtc;

        @JsonProperty("ts")
        private Instant timestamp;

        @JsonProperty("changeUtc24h")
        private BigDecimal changeUtc24h;
    }
}
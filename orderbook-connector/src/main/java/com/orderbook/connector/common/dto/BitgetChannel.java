package com.orderbook.connector.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class BitgetChannel {

    @JsonProperty("instType")
    private MarketType marketType;

    @JsonProperty("channel")
    private ChannelType channelType;

    @JsonProperty("instId")
    private String instrumentId;

    @Getter
    @AllArgsConstructor
    public static enum MarketType {
        //现货
        SPOT("SPOT");

        @JsonValue
        private final String value;

        @Override
        public String toString() {
            return value;
        }
    }

    @Getter
    @AllArgsConstructor
    public static enum ChannelType {
        //行情数据
        TICKER("ticker"),

        //订单深度数据
        DEPTH("books"),
        DEPTH1("books1"),
        DEPTH5("books5"),
        DEPTH15("books15"),

        //成交明细
        FILL("fill"),

        //订单频道
        ORDERS("orders"),

        //账户频道
        ACCOUNT("account");

        @JsonValue
        private final String value;

        @Override
        public String toString() {
            return value;
        }
    }
}
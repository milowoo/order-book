package com.orderbook.connector.stream.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

public class BaseBinanceWebSocketTransaction {

    public enum BinanceWebSocketTypes {
        DEPTH_UPDATE("depthUpdate"),
        TICKER_24_HR("24hrTicker"),
        BOOK_TICKER("bookTicker"),
        KLINE("kLine"),
        AGG_TRADE("aggTrade"),
        TRADE("trade"),
        OUTBOUND_ACCOUNT_POSITION("outboundAccountPosition"),
        EXECUTION_REPORT("executionReport");

        private String serializedValue;

        BinanceWebSocketTypes(String serializedValue) {
            this.serializedValue = serializedValue;
        }

        public String getSerializedValue() {
            return serializedValue;
        }

        /**
         * Get a type from the `type` string of a `ProductBinanceWebSocketTransaction`.
         *
         * Params: value – The string representation.
         * Returns: THe enum value.
         */
        public static BinanceWebSocketTypes fromTransactionValue(String value) {
            for (BinanceWebSocketTypes type : BinanceWebSocketTypes.values()) {
                if (type.serializedValue.equals(value)) {
                    return type;
                }
            }
            return null;
        }
    }

    protected final BinanceWebSocketTypes eventType;
    protected final Date eventTime;

    public BaseBinanceWebSocketTransaction(@JsonProperty("e") String _eventType, @JsonProperty("E") String _eventTime) {
        this(
                BinanceWebSocketTypes.fromTransactionValue(_eventType),
                new Date(Long.parseLong(_eventTime))
        );
    }

    BaseBinanceWebSocketTransaction(BinanceWebSocketTypes eventType, Date eventTime) {
        this.eventType = eventType;
        this.eventTime = eventTime;
    }

    public BinanceWebSocketTypes getEventType() {
        return eventType;
    }

    public Date getEventTime() {
        return eventTime;
    }
}
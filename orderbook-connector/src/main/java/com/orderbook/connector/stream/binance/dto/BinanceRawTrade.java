package com.orderbook.connector.stream.binance.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BinanceRawTrade {
    private final String eventType;
    private final String eventTime;
    private final String symbol;
    private final long tradeId;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final long buyerOrderId;
    private final long sellerOrderId;
    private final long timestamp;
    private final boolean buyerMarketMaker;
    private final boolean ignore;

    public BinanceRawTrade(
            String eventType,
            String eventTime,
            String symbol,
            long tradeId,
            BigDecimal price,
            BigDecimal quantity,
            long buyerOrderId,
            long sellerOrderId,
            long timestamp,
            boolean buyerMarketMaker,
            boolean ignore
    ) {
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.symbol = symbol;
        this.tradeId = tradeId;
        this.price = price;
        this.quantity = quantity;
        this.buyerOrderId = buyerOrderId;
        this.sellerOrderId = sellerOrderId;
        this.timestamp = timestamp;
        this.buyerMarketMaker = buyerMarketMaker;
        this.ignore = ignore;
    }
}
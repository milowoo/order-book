package com.orderbook.connector.stream.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.binance.dto.marketdata.BinanceBookTicker;
import java.math.BigDecimal;
import java.util.Date;

public class BookTickerBinanceWebSocketTransaction extends BaseBinanceWebSocketTransaction {

    private final BinanceBookTicker ticker;

    public BookTickerBinanceWebSocketTransaction(
            @JsonProperty("u") long updateId,
            @JsonProperty("s") String symbol,
            @JsonProperty("b") BigDecimal bidPrice,
            @JsonProperty("B") BigDecimal bidQty,
            @JsonProperty("a") BigDecimal askPrice,
            @JsonProperty("A") BigDecimal askQty
    ) {
        super(BinanceWebSocketTypes.BOOK_TICKER, new Date());
        ticker = new BinanceBookTicker(bidPrice, bidQty, askPrice, askQty, symbol);
        ticker.setUpdateId(updateId);
    }

    public BinanceBookTicker getTicker() {
        return ticker;
    }
}
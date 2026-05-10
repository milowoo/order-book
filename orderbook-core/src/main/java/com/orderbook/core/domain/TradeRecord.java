package com.orderbook.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Record of a single fill/trade for PnL tracking.
 */
@Data
@Builder
@AllArgsConstructor
public class TradeRecord {
    private String tradeId;
    private String symbol;
    private String side;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal amount;
    private BigDecimal fee;
    private String feeCurrency;
    private String exchange;
    private long tradeTime;
}

package com.orderbook.core.domain;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Snapshot of PnL for a single symbol.
 */
@Data
@Builder
public class PnlSnapshot {
    private String symbol;
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private BigDecimal totalFees;
    private BigDecimal totalVolume;
    private BigDecimal currentPosition;
    private BigDecimal entryPrice;
    private long lastUpdated;
    private int tradeCount;
}

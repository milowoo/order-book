package com.orderbook.core.strategy.spread;

import com.orderbook.core.domain.SymbolBo;

import java.math.BigDecimal;

/**
 * Calculates the price offset (spread) for market-making orders.
 * <p>
 * Ask prices:  referencePrice + offset (sell at a premium)
 * Bid prices:  referencePrice - offset (buy at a discount)
 */
public interface SpreadCalculator {

    /**
     * Calculate the price offset for one side of the order book.
     *
     * @param symbol   trading pair key (e.g. "BTCUSDT")
     * @param isBid    true for bid side, false for ask side
     * @param symbolBo full symbol metadata (tick size, base/quote tokens, etc.)
     * @return positive price offset in price units (same scale as tickSize)
     */
    BigDecimal calculateOffset(String symbol, boolean isBid, SymbolBo symbolBo);

    /** Human-readable calculator name for logging. */
    String getName();
}

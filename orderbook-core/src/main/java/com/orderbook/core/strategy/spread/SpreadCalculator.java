package com.orderbook.core.strategy.spread;

import com.orderbook.core.domain.SymbolBo;

import java.math.BigDecimal;

/**
 * 计算做市订单的价格偏移量（价差）。
 * <p>
 * 卖单价格：参考价格 + 偏移量（以溢价卖出）
 * 买单价格：参考价格 - 偏移量（以折价买入）
 */
public interface SpreadCalculator {

    /**
     * Calculate the price offset for one side of the order book.
     * 计算订单簿某一侧的价格偏移量。
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

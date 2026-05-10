package com.orderbook.strategy.risk;

import java.math.BigDecimal;

/**
 * 责任链中的单个风险检查环节。
 * 实现类应当是无状态的，或者仅存储配置级别的状态。
 */
public interface RiskCheck {

    /**
     * Check whether an order at the given price/quantity is allowed.
     * @param symbol trading pair symbol
     * @param side "buy" or "sell"
     * @param price order price
     * @param quantity order quantity
     * @return true if the order is allowed, false if it should be rejected
     */
    boolean check(String symbol, String side, BigDecimal price, BigDecimal quantity);

    /** Human-readable name for logging. */
    String getName();
}

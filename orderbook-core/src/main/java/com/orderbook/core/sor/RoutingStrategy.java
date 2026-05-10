package com.orderbook.core.sor;

/**
 * SOR exchange selection strategy.
 */
public enum RoutingStrategy {
    /** Pick exchange with lowest taker fee. */
    LOWEST_FEE,
    /** Pick exchange with lowest historical latency. */
    FASTEST,
    /** Pick exchange with deepest liquidity. */
    BEST_LIQUIDITY,
    /** Weighted combination of fee, latency, and liquidity scores. */
    WEIGHTED
}

package com.orderbook.core.strategy;

import java.util.Map;

/**
 * Base interface for all trading strategies.
 * Implementations are Spring beans injected by the StrategyExecutor.
 */
public interface Strategy {

    /** Unique strategy name for logging and monitoring. */
    String getName();

    /**
     * Execute one tick of the strategy.
     * @param symbol the trading pair symbol (e.g. "BTCUSDT")
     * @param context per-symbol state context shared across strategies
     */
    void execute(String symbol, Map<String, String> context);
}

package com.orderbook.core.strategy;

import java.util.Map;

/**
 * 所有交易策略的基础接口。
 * 具体实现类是由 StrategyExecutor 注入的 Spring Bean。
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

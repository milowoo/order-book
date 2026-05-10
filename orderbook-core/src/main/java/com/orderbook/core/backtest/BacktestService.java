package com.orderbook.core.backtest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BacktestService {

    private final BacktestEngine backtestEngine;
    private final Map<String, BacktestResult> results = new ConcurrentHashMap<>();

    public BacktestService(BacktestEngine backtestEngine) {
        this.backtestEngine = backtestEngine;
    }

    /**
     * Run a backtest synchronously.
     */
    public BacktestResult run(BacktestConfig config) {
        log.info("[BacktestService] Starting backtest: symbol={}, model={}, range={}-{}",
                config.getSymbol(), config.getModel(), config.getStartTime(), config.getEndTime());
        BacktestResult result = backtestEngine.run(config);
        results.put(result.getId(), result);
        return result;
    }

    /**
     * Run a backtest asynchronously.
     */
    public CompletableFuture<BacktestResult> runAsync(BacktestConfig config) {
        return CompletableFuture.supplyAsync(() -> run(config));
    }

    /**
     * List all cached backtest results.
     */
    public List<BacktestResult> listResults() {
        return List.copyOf(results.values());
    }

    /**
     * Get a specific backtest result by ID.
     */
    public BacktestResult getResult(String id) {
        return results.get(id);
    }
}

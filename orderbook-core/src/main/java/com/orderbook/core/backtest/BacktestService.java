package com.orderbook.core.backtest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BacktestService {

    private final BacktestEngine backtestEngine;
    private final CsvSnapshotLoader csvSnapshotLoader;
    private final Map<String, BacktestResult> results = new ConcurrentHashMap<>();

    public BacktestService(BacktestEngine backtestEngine) {
        this.backtestEngine = backtestEngine;
        this.csvSnapshotLoader = new CsvSnapshotLoader();
    }

    /**
     * Run a backtest synchronously from DB snapshots.
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
     * Run a backtest from CSV data.
     */
    public BacktestResult runFromCsv(BacktestConfig config, InputStream csvStream) {
        log.info("[BacktestService] Starting CSV backtest: symbol={}, model={}",
                config.getSymbol(), config.getModel());
        try {
            List<BacktestSnapshot> snapshots = csvSnapshotLoader.loadCsv(csvStream);
            if (snapshots.isEmpty()) {
                log.warn("[BacktestService] CSV contained no valid snapshots");
                BacktestResult empty = new BacktestResult();
                empty.setId("empty");
                empty.setSymbol(config.getSymbol());
                empty.setModel(config.getModel());
                empty.setInitialCapital(config.getInitialCapital());
                empty.setFinalBalance(config.getInitialCapital());
                empty.setTotalTrades(0);
                return empty;
            }
            BacktestResult result = backtestEngine.simulate(config, snapshots);
            results.put(result.getId(), result);
            return result;
        } catch (Exception e) {
            log.error("[BacktestService] CSV backtest failed", e);
            throw new RuntimeException("CSV backtest failed: " + e.getMessage(), e);
        }
    }

    /**
     * Cache a result (used by controller for CSV uploads).
     */
    public void cacheResult(BacktestResult result) {
        if (result != null && result.getId() != null) {
            results.put(result.getId(), result);
        }
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

    /**
     * Clear all cached results.
     */
    public void clearResults() {
        results.clear();
        log.info("[BacktestService] Cleared all cached results");
    }
}

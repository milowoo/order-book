package com.orderbook.core.backtest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /**
     * Run a backtest with the given configuration.
     * Synchronous: waits for result.
     */
    @PostMapping("/run")
    public ResponseEntity<BacktestResult> runBacktest(@RequestBody BacktestConfig config) {
        log.info("[BacktestController] POST /api/backtest/run: symbol={}, model={}",
                config.getSymbol(), config.getModel());
        try {
            BacktestResult result = backtestService.run(config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[BacktestController] Backtest failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * List all cached backtest results.
     */
    @GetMapping("/results")
    public ResponseEntity<List<BacktestResult>> listResults() {
        return ResponseEntity.ok(backtestService.listResults());
    }

    /**
     * Get a specific backtest result by ID.
     */
    @GetMapping("/results/{id}")
    public ResponseEntity<BacktestResult> getResult(@PathVariable String id) {
        BacktestResult result = backtestService.getResult(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}

package com.orderbook.core.backtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private final BacktestService backtestService;
    private final ObjectMapper objectMapper;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Run a backtest with the given configuration (from DB snapshots).
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
     * Run a backtest from an uploaded CSV file.
     */
    @PostMapping(value = "/run-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BacktestResult> runCsvBacktest(
            @RequestParam("file") MultipartFile file,
            @RequestParam("config") String configJson) {
        log.info("[BacktestController] POST /api/backtest/run-csv: file={}", file.getOriginalFilename());
        try {
            BacktestConfig config = objectMapper.readValue(configJson, BacktestConfig.class);
            BacktestResult result = backtestService.runFromCsv(config, file.getInputStream());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[BacktestController] CSV backtest failed", e);
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

    /**
     * Get a text report for a backtest result.
     */
    @GetMapping("/results/{id}/report")
    public ResponseEntity<String> getReport(@PathVariable String id) {
        BacktestResult result = backtestService.getResult(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        BacktestReport report = new BacktestReport();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(report.generateDetail(result));
    }

    /**
     * Clear all cached results.
     */
    @DeleteMapping("/results")
    public ResponseEntity<Void> clearResults() {
        backtestService.clearResults();
        return ResponseEntity.noContent().build();
    }
}

package com.orderbook.core.arbitrage;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for cross-exchange arbitrage monitoring.
 */
@RestController
@RequestMapping("/api/arbitrage")
public class ArbitrageController {

    private final ArbitrageService arbitrageService;

    public ArbitrageController(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    /**
     * Get latest arbitrage opportunities for all symbols.
     */
    @GetMapping("/opportunities")
    public ResponseEntity<Map<String, List<ArbitrageOpportunity>>> getOpportunities() {
        return ResponseEntity.ok(arbitrageService.getLatestOpportunities());
    }

    /**
     * Get recent opportunity history.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ArbitrageOpportunity>> getHistory() {
        return ResponseEntity.ok(arbitrageService.getRecentOpportunities());
    }

    /**
     * Get arbitrage scan statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(arbitrageService.getStats());
    }

    /**
     * Manually trigger a scan for a symbol (for testing/debugging).
     */
    @PostMapping("/scan/{symbol}")
    public ResponseEntity<List<ArbitrageOpportunity>> scanSymbol(@PathVariable String symbol) {
        List<ArbitrageOpportunity> opps = arbitrageService.scan(symbol);
        return ResponseEntity.ok(opps);
    }
}

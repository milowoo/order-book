package com.orderbook.core.arbitrage;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用于跨交易所套利监控的 REST API 接口。
 */
@RestController
@RequestMapping("/api/arbitrage")
public class ArbitrageController {

    private final ArbitrageService arbitrageService;

    public ArbitrageController(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    /**
     * 获取所有交易标的的最新套利机会。
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
     * 获取套利扫描的统计数据。
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(arbitrageService.getStats());
    }

    /**
     * Manually trigger a scan for a symbol (for testing/debugging).
     * 手动触发指定交易标的的扫描（用于测试或调试）。
     */
    @PostMapping("/scan/{symbol}")
    public ResponseEntity<List<ArbitrageOpportunity>> scanSymbol(@PathVariable String symbol) {
        List<ArbitrageOpportunity> opps = arbitrageService.scan(symbol);
        return ResponseEntity.ok(opps);
    }
}

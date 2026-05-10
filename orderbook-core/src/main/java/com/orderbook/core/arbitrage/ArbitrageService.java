package com.orderbook.core.arbitrage;

import com.orderbook.core.config.ApolloConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service that manages cross-exchange arbitrage scanning, tracking, and signal export.
 */
@Slf4j
@Service
public class ArbitrageService {

    private final ArbitrageDetector detector;
    private final ApolloConfig apolloConfig;

    // Latest opportunities per symbol (ring buffer)
    private final Map<String, List<ArbitrageOpportunity>> latestOpportunities = new ConcurrentHashMap<>();
    private final List<ArbitrageOpportunity> recentOpportunities = new CopyOnWriteArrayList<>();
    private static final int MAX_RECENT = 100;

    // Counters
    private long totalScanned = 0;
    private long totalOpportunities = 0;

    public ArbitrageService(ArbitrageDetector detector, ApolloConfig apolloConfig) {
        this.detector = detector;
        this.apolloConfig = apolloConfig;
    }

    /**
     * Scan for arbitrage opportunities for a symbol.
     * Called from strategy tick.
     *
     * @return list of opportunities (best first), or empty list if none
     */
    public List<ArbitrageOpportunity> scan(String symbol) {
        ArbitrageConfig config = loadConfig();
        if (!config.isEnabled()) return List.of();

        List<ArbitrageOpportunity> opportunities = detector.scan(symbol, config);

        totalScanned++;

        if (!opportunities.isEmpty()) {
            opportunities.sort((a, b) -> b.getNetProfit().compareTo(a.getNetProfit()));
            latestOpportunities.put(symbol, opportunities);
            recentOpportunities.addAll(opportunities);
            totalOpportunities += opportunities.size();

            // Trim recent
            while (recentOpportunities.size() > MAX_RECENT) {
                recentOpportunities.remove(0);
            }

            // Log top opportunity
            ArbitrageOpportunity best = opportunities.get(0);
            if (log.isInfoEnabled()) {
                log.info("[Arbitrage] {}: {} {} → {} | buy={} sell={} profit={} fee={} net={} qty={}",
                        symbol, best.isExecutable() ? "EXEC" : "DETECT",
                        best.getBuyExchange(), best.getSellExchange(),
                        best.getBuyPrice(), best.getSellPrice(),
                        best.getTheoreticalProfit(), best.getFee(),
                        best.getNetProfit(), best.getMaxQuantity());
            }
        }

        return opportunities;
    }

    /**
     * Get the best executable opportunity for a symbol.
     * Returns null if no opportunity qualifies for execution.
     */
    public ArbitrageOpportunity getBestExecutable(String symbol) {
        List<ArbitrageOpportunity> opps = latestOpportunities.get(symbol);
        if (opps == null || opps.isEmpty()) return null;

        return opps.stream()
                .filter(ArbitrageOpportunity::isExecutable)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the latest opportunities for all symbols.
     */
    public Map<String, List<ArbitrageOpportunity>> getLatestOpportunities() {
        return Collections.unmodifiableMap(latestOpportunities);
    }

    /**
     * Get recent opportunity history.
     */
    public List<ArbitrageOpportunity> getRecentOpportunities() {
        return Collections.unmodifiableList(recentOpportunities);
    }

    /**
     * Get scan statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalScanned", totalScanned);
        stats.put("totalOpportunities", totalOpportunities);
        stats.put("symbolsWithOpportunities", latestOpportunities.size());
        return stats;
    }

    private ArbitrageConfig loadConfig() {
        ArbitrageConfig config = new ArbitrageConfig();
        config.setEnabled(apolloConfig.getArbitrageEnabled());
        config.setMinProfitUsdt(BigDecimal.valueOf(apolloConfig.getArbitrageMinProfitUsdt()));
        config.setMaxOrderQty(apolloConfig.getArbitrageMaxOrderQty());
        return config;
    }
}

package com.orderbook.core.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-symbol spread calculator configuration.
 * Prefix: strategy.spread
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "strategy.spread")
public class SpreadConfig {

    private Map<String, SymbolSpreadConfig> symbols = new HashMap<>();

    public SymbolSpreadConfig getSymbolConfig(String symbol) {
        return symbols.getOrDefault(symbol, new SymbolSpreadConfig());
    }

    @Data
    public static class SymbolSpreadConfig {
        /** Which model to use: "constant", "inventory", "risk", "hybrid" */
        private String model = "constant";

        /** Base offset measured in ticks (multiplied by tickSize at runtime) */
        private BigDecimal baseOffsetTicks = BigDecimal.ONE;

        // --- Inventory model ---
        private BigDecimal targetPosition = BigDecimal.ZERO;
        private BigDecimal maxPosition = BigDecimal.ONE;
        private BigDecimal skewFactor = new BigDecimal("0.5");

        // --- Risk (volatility) model ---
        private BigDecimal volCoeff = new BigDecimal("2.0");
        private int volatilityWindowSize = 20;

        // --- Hybrid model weights ---
        private BigDecimal constantWeight = new BigDecimal("0.3");
        private BigDecimal inventoryWeight = new BigDecimal("0.4");
        private BigDecimal riskWeight = new BigDecimal("0.3");

        // --- Alpha model ---
        /** Whether to enable alpha signal integration. */
        private boolean alphaEnabled = false;
        /** ML model type: "random_forest", "xgboost", or null/empty to disable. */
        private String alphaModelType = null;
        /** Path to serialized ML model file. */
        private String alphaModelPath = null;
        /** ML model name for logging. */
        private String alphaModelName = "default";
        /** Weight of ML signal in composite alpha. */
        private double alphaMlWeight = 0.3;
        /** Order flow imbalance: number of depth levels. */
        private int alphaOrderFlowDepth = 10;
        /** Order flow imbalance: weight in composite. */
        private double alphaOrderFlowWeight = 0.5;
        /** Momentum: lookback periods. */
        private int alphaMomentumLookback = 5;
        /** Momentum: weight in composite. */
        private double alphaMomentumWeight = 0.5;
        /** Max adjustment to target position from alpha (in same unit as targetPosition). */
        private BigDecimal alphaMaxPositionAdjustment = new BigDecimal("0.5");
    }
}

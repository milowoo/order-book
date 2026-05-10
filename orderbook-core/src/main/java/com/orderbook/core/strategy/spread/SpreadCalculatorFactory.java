package com.orderbook.core.strategy.spread;

import com.orderbook.core.config.SpreadConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory that builds and caches SpreadCalculator instances per symbol.
 * Configuration is read from SpreadConfig (strategy.spread.symbols.*).
 */
@Slf4j
@Service
public class SpreadCalculatorFactory {

    private final SpreadConfig spreadConfig;
    private final VolatilityTracker volatilityTracker;
    private final Map<String, SpreadCalculator> calculatorCache = new ConcurrentHashMap<>();

    public SpreadCalculatorFactory(SpreadConfig spreadConfig, VolatilityTracker volatilityTracker) {
        this.spreadConfig = spreadConfig;
        this.volatilityTracker = volatilityTracker;
    }

    @PostConstruct
    public void init() {
        log.info("SpreadCalculatorFactory initialized. Configured symbols: {}", spreadConfig.getSymbols().keySet());
    }

    /**
     * Get (or create) the cached spread calculator for a symbol.
     */
    public SpreadCalculator getCalculator(String symbol) {
        return calculatorCache.computeIfAbsent(symbol, this::buildCalculator);
    }

    private SpreadCalculator buildCalculator(String symbol) {
        SpreadConfig.SymbolSpreadConfig config = spreadConfig.getSymbolConfig(symbol);
        log.info("[{}] Building spread calculator: model={}, baseOffsetTicks={}",
                symbol, config.getModel(), config.getBaseOffsetTicks());

        // Configure volatility tracker window
        volatilityTracker.setWindowSize(symbol, config.getVolatilityWindowSize());

        return switch (config.getModel().toLowerCase()) {
            case "constant" ->
                new ConstantSpreadCalculator(config.getBaseOffsetTicks());
            case "inventory" ->
                new InventoryBasedSpreadCalculator(
                        config.getBaseOffsetTicks(),
                        config.getTargetPosition(),
                        config.getMaxPosition(),
                        config.getSkewFactor());
            case "risk" ->
                new RiskAdjustedSpreadCalculator(
                        config.getBaseOffsetTicks(),
                        config.getVolCoeff(),
                        volatilityTracker);
            case "hybrid" ->
                buildHybridCalculator(symbol, config);
            default -> {
                log.warn("[{}] Unknown spread model '{}', falling back to constant", symbol, config.getModel());
                yield new ConstantSpreadCalculator(config.getBaseOffsetTicks());
            }
        };
    }

    private SpreadCalculator buildHybridCalculator(String symbol, SpreadConfig.SymbolSpreadConfig config) {
        BigDecimal base = config.getBaseOffsetTicks();

        ConstantSpreadCalculator constantCalc = new ConstantSpreadCalculator(base);
        InventoryBasedSpreadCalculator inventoryCalc = new InventoryBasedSpreadCalculator(
                base, config.getTargetPosition(), config.getMaxPosition(), config.getSkewFactor());
        RiskAdjustedSpreadCalculator riskCalc = new RiskAdjustedSpreadCalculator(
                base, config.getVolCoeff(), volatilityTracker);

        List<HybridSpreadCalculator.WeightedCalculator> weighted = List.of(
                new HybridSpreadCalculator.WeightedCalculator(constantCalc, config.getConstantWeight()),
                new HybridSpreadCalculator.WeightedCalculator(inventoryCalc, config.getInventoryWeight()),
                new HybridSpreadCalculator.WeightedCalculator(riskCalc, config.getRiskWeight())
        );

        return new HybridSpreadCalculator(weighted);
    }

    /** Invalidate cached calculator for a symbol (for dynamic reconfiguration). */
    public void invalidate(String symbol) {
        calculatorCache.remove(symbol);
    }
}

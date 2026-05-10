package com.orderbook.core.strategy.spread;

import com.orderbook.core.config.SpreadConfig;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.strategy.alpha.AlphaAggregator;
import com.orderbook.core.strategy.alpha.AlphaConfig;
import com.orderbook.core.strategy.ml.MLModel;
import com.orderbook.core.strategy.ml.RandomForestModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
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
    private final OrderBookStore orderBookStore;
    private final Map<String, SpreadCalculator> calculatorCache = new ConcurrentHashMap<>();
    private final AlphaAggregator alphaAggregator;

    public SpreadCalculatorFactory(SpreadConfig spreadConfig,
                                   VolatilityTracker volatilityTracker,
                                   OrderBookStore orderBookStore) {
        this.spreadConfig = spreadConfig;
        this.volatilityTracker = volatilityTracker;
        this.orderBookStore = orderBookStore;
        this.alphaAggregator = new AlphaAggregator(orderBookStore, volatilityTracker);
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

        // Configure alpha signals if enabled
        if (config.isAlphaEnabled()) {
            AlphaConfig alphaConfig = AlphaConfig.builder()
                    .orderFlowEnabled(true)
                    .orderFlowDepth(config.getAlphaOrderFlowDepth())
                    .orderFlowWeight(config.getAlphaOrderFlowWeight())
                    .momentumEnabled(true)
                    .momentumLookback(config.getAlphaMomentumLookback())
                    .momentumWeight(config.getAlphaMomentumWeight())
                    .maxAlphaPositionAdjustment(config.getAlphaMaxPositionAdjustment().doubleValue())
                    .build();
            alphaAggregator.configure(symbol, alphaConfig);

            // Load ML model if configured
            String modelType = config.getAlphaModelType();
            if (modelType != null && !modelType.isEmpty()) {
                try {
                    MLModel mlModel = switch (modelType.toLowerCase()) {
                        case "random_forest" -> {
                            String path = config.getAlphaModelPath();
                            if (path != null && !path.isEmpty()) {
                                yield RandomForestModel.load(new File(path));
                            }
                            log.warn("[{}] Random Forest model type selected but no modelPath provided", symbol);
                            yield null;
                        }
                        default -> {
                            log.warn("[{}] Unsupported ML model type: {}", symbol, modelType);
                            yield null;
                        }
                    };
                    if (mlModel != null) {
                        alphaAggregator.registerMLModel(symbol, mlModel);
                    }
                } catch (Exception e) {
                    log.error("[{}] Failed to load ML model: {}", symbol, e.getMessage());
                }
            }

            log.info("[{}] Alpha signals configured: orderFlow(depth={}), momentum(lookback={}){}",
                    symbol, config.getAlphaOrderFlowDepth(), config.getAlphaMomentumLookback(),
                    modelType != null ? ", ml=" + modelType : "");
        }

        return switch (config.getModel().toLowerCase()) {
            case "constant" ->
                new ConstantSpreadCalculator(config.getBaseOffsetTicks());
            case "inventory" ->
                new InventoryBasedSpreadCalculator(
                        config.getBaseOffsetTicks(),
                        config.getTargetPosition(),
                        config.getMaxPosition(),
                        config.getSkewFactor(),
                        config.isAlphaEnabled() ? alphaAggregator : null,
                        config.getAlphaMaxPositionAdjustment());
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
                base, config.getTargetPosition(), config.getMaxPosition(), config.getSkewFactor(),
                config.isAlphaEnabled() ? alphaAggregator : null,
                config.getAlphaMaxPositionAdjustment());
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

package com.orderbook.core.strategy.alpha;

import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.strategy.ml.MLAlphaSignal;
import com.orderbook.core.strategy.ml.MLModel;
import com.orderbook.core.strategy.spread.VolatilityTracker;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 Alpha 聚合服务。
 管理每个交易对的 Alpha 信号计算。负责缓存由配置好的信号（如订单流不平衡、动量、机器学习模型等）构建的 CompositeAlpha 实例。
 这个复合 Alpha 信号会输入到基于库存的价差计算器中，以动态调整目标持仓量：
 正 Alpha（看涨）→ 提高目标持仓量 → 积累基础代币
 负 Alpha（看跌）→ 降低目标持仓量 → 减少基础代币
 */

@Slf4j
public class AlphaAggregator {

    private final OrderBookStore orderBookStore;
    private final VolatilityTracker volatilityTracker;
    private final Map<String, AlphaConfig> symbolConfigs = new ConcurrentHashMap<>();
    private final Map<String, CompositeAlpha> alphaInstances = new ConcurrentHashMap<>();
    private final Map<String, MLModel> mlModels = new ConcurrentHashMap<>();

    public AlphaAggregator(OrderBookStore orderBookStore, VolatilityTracker volatilityTracker) {
        this.orderBookStore = orderBookStore;
        this.volatilityTracker = volatilityTracker;
    }

    /**
     * Configure the alpha signals for a symbol.
     */
    public void configure(String symbol, AlphaConfig config) {
        symbolConfigs.put(symbol, config);
        alphaInstances.remove(symbol);
    }

    /**
     * Register an ML model for a symbol.
     */
    public void registerMLModel(String symbol, MLModel model) {
        mlModels.put(symbol, model);
        alphaInstances.remove(symbol); // rebuild composite
        log.info("[{}] Registered ML model '{}' with {} features", symbol, model.getName(), model.featureCount());
    }

    /**
     * Get the composite alpha value for a symbol.
     */
    public double getAlpha(String symbol, SymbolBo symbolBo) {
        CompositeAlpha composite = alphaInstances.computeIfAbsent(symbol, s -> buildComposite(s));
        return composite.computeAlpha(symbol, symbolBo);
    }

    private CompositeAlpha buildComposite(String symbol) {
        CompositeAlpha composite = new CompositeAlpha();
        AlphaConfig config = symbolConfigs.getOrDefault(symbol, AlphaConfig.defaultConfig());

        if (config.isOrderFlowEnabled()) {
            composite.addSignal(
                    new OrderFlowImbalanceAlpha(orderBookStore, config.getOrderFlowDepth()),
                    config.getOrderFlowWeight());
        }
        if (config.isMomentumEnabled()) {
            composite.addSignal(
                    new MomentumAlpha(volatilityTracker, config.getMomentumLookback()),
                    config.getMomentumWeight());
        }

        // ML model signal (if available)
        MLModel mlModel = mlModels.get(symbol);
        if (mlModel != null) {
            composite.addSignal(
                    new MLAlphaSignal(orderBookStore, volatilityTracker, mlModel),
                    config.getMlAlphaWeight());
            log.info("[{}] Added ML alpha signal '{}' with weight {}",
                    symbol, mlModel.getName(), config.getMlAlphaWeight());
        }

        log.info("[{}] Built CompositeAlpha: orderFlow(weight={},depth={}), momentum(weight={},lookback={}){}",
                symbol,
                config.getOrderFlowWeight(), config.getOrderFlowDepth(),
                config.getMomentumWeight(), config.getMomentumLookback(),
                mlModel != null ? ", ml=" + mlModel.getName() : "");

        return composite;
    }

    /**
     * Get alpha-adjusted target position.
     */
    public double adjustTargetPosition(String symbol, SymbolBo symbolBo,
                                        double baseTarget, double maxAdjustment) {
        double alpha = getAlpha(symbol, symbolBo);
        return baseTarget + alpha * maxAdjustment;
    }
}

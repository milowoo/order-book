package com.orderbook.core.strategy.alpha;

import com.orderbook.core.domain.SymbolBo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 复合 Alpha 信号。
 通过可配置的权重，聚合多个 Alpha 信号。
 最终的 Alpha 值是一个归一化到 [-1, 1] 区间的加权平均值。
 */
@Slf4j
public class CompositeAlpha implements AlphaSignal {

    private final List<WeightedSignal> signals = new ArrayList<>();

    public CompositeAlpha addSignal(AlphaSignal signal, double weight) {
        signals.add(new WeightedSignal(signal, weight));
        return this;
    }

    @Override
    public double computeAlpha(String symbol, SymbolBo symbolBo) {
        if (signals.isEmpty()) return 0.0;

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (WeightedSignal ws : signals) {
            try {
                double alpha = ws.signal.computeAlpha(symbol, symbolBo);
                weightedSum += alpha * ws.weight;
                totalWeight += ws.weight;

                if (log.isDebugEnabled()) {
                    log.debug("[{}] Alpha '{}': raw={}, weight={}", symbol, ws.signal.getName(), alpha, ws.weight);
                }
            } catch (Exception e) {
                log.warn("[{}] Alpha signal '{}' failed: {}", symbol, ws.signal.getName(), e.getMessage());
            }
        }

        if (totalWeight < 1e-8) return 0.0;
        double composite = weightedSum / totalWeight;

        // Clamp to [-1, 1]
        return Math.max(-1.0, Math.min(1.0, composite));
    }

    @Override
    public String getName() {
        return "compositeAlpha";
    }

    private record WeightedSignal(AlphaSignal signal, double weight) {}
}

package com.orderbook.core.strategy.spread;

import com.orderbook.core.domain.SymbolBo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 混合型价差计算器 —— 多个价差计算器的加权组合。
 结果 = (权重1 * 偏移量1 + 权重2 * 偏移量2 + ...) / (权重1 + 权重2 + ...)
 */
@Slf4j
public class HybridSpreadCalculator implements SpreadCalculator {

    private final List<WeightedCalculator> calculators;

    public HybridSpreadCalculator(List<WeightedCalculator> calculators) {
        if (calculators == null || calculators.isEmpty()) {
            throw new IllegalArgumentException("HybridSpreadCalculator requires at least one weighted calculator");
        }
        this.calculators = calculators;
    }

    @Data
    @AllArgsConstructor
    public static class WeightedCalculator {
        private final SpreadCalculator calculator;
        private final BigDecimal weight;
    }

    @Override
    public BigDecimal calculateOffset(String symbol, boolean isBid, SymbolBo symbolBo) {
        BigDecimal tickSize = symbolBo.getTickSize();
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (WeightedCalculator wc : calculators) {
            BigDecimal offset = wc.getCalculator().calculateOffset(symbol, isBid, symbolBo);
            weightedSum = weightedSum.add(offset.multiply(wc.getWeight()));
            totalWeight = totalWeight.add(wc.getWeight());
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("[{}] Hybrid spread total weight is zero, returning 1 tick", symbol);
            return BigDecimal.ONE.multiply(tickSize).setScale(tickSize.scale(), RoundingMode.HALF_UP);
        }

        return weightedSum.divide(totalWeight, tickSize.scale(), RoundingMode.HALF_UP);
    }

    @Override
    public String getName() {
        return "hybrid";
    }
}

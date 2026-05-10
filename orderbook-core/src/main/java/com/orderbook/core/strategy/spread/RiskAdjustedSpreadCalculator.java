package com.orderbook.core.strategy.spread;

import com.orderbook.core.domain.SymbolBo;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 风险调整型价差计算器 —— 在高波动期间扩大价差。
 偏移量 = 基础偏移量 * (1 + 波动系数 * 波动率)
 买单和卖单两侧的偏移量相同（对称性扩大）。
 */
@Slf4j
public class RiskAdjustedSpreadCalculator implements SpreadCalculator {

    private final BigDecimal baseOffsetTicks;
    private final BigDecimal volCoeff;
    private final VolatilityTracker volatilityTracker;

    public RiskAdjustedSpreadCalculator(BigDecimal baseOffsetTicks,
                                        BigDecimal volCoeff,
                                        VolatilityTracker volatilityTracker) {
        this.baseOffsetTicks = baseOffsetTicks;
        this.volCoeff = volCoeff;
        this.volatilityTracker = volatilityTracker;
    }

    @Override
    public BigDecimal calculateOffset(String symbol, boolean isBid, SymbolBo symbolBo) {
        BigDecimal tickSize = symbolBo.getTickSize();
        BigDecimal baseOffset = baseOffsetTicks.multiply(tickSize);

        BigDecimal volatility = volatilityTracker.getVolatility(symbol);

        // multiplier = 1 + volCoeff * volatility, lower-bounded at 1.0
        BigDecimal multiplier = BigDecimal.ONE.add(volCoeff.multiply(volatility));
        if (multiplier.compareTo(BigDecimal.ONE) < 0) {
            multiplier = BigDecimal.ONE;
        }

        BigDecimal offset = baseOffset.multiply(multiplier);
        BigDecimal result = offset.setScale(tickSize.scale(), RoundingMode.HALF_UP);

        if (log.isDebugEnabled() && volatility.compareTo(BigDecimal.ZERO) > 0) {
            log.debug("[{}] Risk-adjusted spread: volatility={}, multiplier={}, offset={}",
                    symbol, volatility, multiplier, result);
        }

        return result;
    }

    @Override
    public String getName() {
        return "riskAdjusted";
    }
}

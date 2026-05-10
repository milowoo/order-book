package com.orderbook.core.strategy.spread;

import com.orderbook.core.domain.SymbolBo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 固定价差计算器 —— 无论市场状况如何，都返回一个固定的偏移量。
 该偏移量是对称的（买单和卖单相同），并且可以以 ticks（最小变动价位）为单位进行配置。
 */
public class ConstantSpreadCalculator implements SpreadCalculator {

    private final BigDecimal baseOffsetTicks;

    public ConstantSpreadCalculator(BigDecimal baseOffsetTicks) {
        this.baseOffsetTicks = baseOffsetTicks;
    }

    @Override
    public BigDecimal calculateOffset(String symbol, boolean isBid, SymbolBo symbolBo) {
        BigDecimal offset = baseOffsetTicks.multiply(symbolBo.getTickSize());
        return offset.setScale(symbolBo.getTickSize().scale(), RoundingMode.HALF_UP);
    }

    @Override
    public String getName() {
        return "constant";
    }
}

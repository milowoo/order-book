package com.orderbook.core.strategy.spread;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.BalanceBo;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.AccountStore;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 基于库存的价差计算器。
 根据当前基础代币持仓相对于目标持仓的偏差，对价差进行倾斜（Skew）调整。
 当持有多头（持仓超过目标值）时：买单价差扩大（抑制买入），
 卖单价差收窄（鼓励卖出）。
 当持有空头时：则相反。
 */
@Slf4j
public class InventoryBasedSpreadCalculator implements SpreadCalculator {

    private static final ExchangeCode EXCHANGE = ExchangeCode.OSL_GLOBAL;

    private final BigDecimal baseOffsetTicks;
    private final BigDecimal targetPosition;
    private final BigDecimal maxPosition;
    private final BigDecimal skewFactor;

    public InventoryBasedSpreadCalculator(BigDecimal baseOffsetTicks,
                                          BigDecimal targetPosition,
                                          BigDecimal maxPosition,
                                          BigDecimal skewFactor) {
        this.baseOffsetTicks = baseOffsetTicks;
        this.targetPosition = targetPosition;
        this.maxPosition = maxPosition;
        this.skewFactor = skewFactor;
    }

    @Override
    public BigDecimal calculateOffset(String symbol, boolean isBid, SymbolBo symbolBo) {
        BigDecimal tickSize = symbolBo.getTickSize();
        BigDecimal baseOffset = baseOffsetTicks.multiply(tickSize);

        // Get current base token balance
        BalanceBo balance = AccountStore.getAccount(EXCHANGE).getBalance(symbolBo.getBaseTokenId());
        BigDecimal currentPosition = balance.getTotal();

        // Compute normalized position ratio, clamped to [-1, 1]
        BigDecimal positionRatio = currentPosition.subtract(targetPosition)
                .divide(maxPosition, 8, RoundingMode.HALF_UP);
        positionRatio = clamp(positionRatio, BigDecimal.valueOf(-1), BigDecimal.ONE);

        // Apply inventory skew
        BigDecimal offset;
        if (isBid) {
            // Bid side: wider spread when long (discourage buying)
            offset = baseOffset.multiply(BigDecimal.ONE.add(skewFactor.multiply(positionRatio)));
        } else {
            // Ask side: narrower spread when long (encourage selling)
            offset = baseOffset.multiply(BigDecimal.ONE.subtract(skewFactor.multiply(positionRatio)));
        }

        // Ensure non-negative
        if (offset.compareTo(BigDecimal.ZERO) < 0) {
            offset = BigDecimal.ZERO;
        }

        BigDecimal result = offset.setScale(tickSize.scale(), RoundingMode.HALF_UP);

        if (log.isDebugEnabled()) {
            log.debug("[{}] Inventory spread: position={}, ratio={}, {} offset={}",
                    symbol, currentPosition, positionRatio, isBid ? "bid" : "ask", result);
        }

        return result;
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) return min;
        if (value.compareTo(max) > 0) return max;
        return value;
    }

    @Override
    public String getName() {
        return "inventory";
    }
}

package com.orderbook.core.strategy.spread;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.BalanceBo;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.AccountStore;
import com.orderbook.core.strategy.alpha.AlphaAggregator;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Inventory-based spread calculator.
 * <p>
 * Adjusts spread based on deviation of current base token position from target.
 * When long (position > target): bid spread widens (discourage buying),
 * ask spread narrows (encourage selling).
 * When short: the opposite.
 * <p>
 * Alpha signal integration: if an AlphaAggregator is provided, the target
 * position is dynamically adjusted based on alpha direction.
 */
@Slf4j
public class InventoryBasedSpreadCalculator implements SpreadCalculator {

    private static final ExchangeCode EXCHANGE = ExchangeCode.OSL_GLOBAL;

    private final BigDecimal baseOffsetTicks;
    private final BigDecimal baseTargetPosition;
    private final BigDecimal maxPosition;
    private final BigDecimal skewFactor;
    private final AlphaAggregator alphaAggregator;
    private final BigDecimal maxAlphaAdjustment;

    public InventoryBasedSpreadCalculator(BigDecimal baseOffsetTicks,
                                          BigDecimal targetPosition,
                                          BigDecimal maxPosition,
                                          BigDecimal skewFactor) {
        this(baseOffsetTicks, targetPosition, maxPosition, skewFactor, null, BigDecimal.ZERO);
    }

    public InventoryBasedSpreadCalculator(BigDecimal baseOffsetTicks,
                                          BigDecimal targetPosition,
                                          BigDecimal maxPosition,
                                          BigDecimal skewFactor,
                                          AlphaAggregator alphaAggregator,
                                          BigDecimal maxAlphaAdjustment) {
        this.baseOffsetTicks = baseOffsetTicks;
        this.baseTargetPosition = targetPosition;
        this.maxPosition = maxPosition;
        this.skewFactor = skewFactor;
        this.alphaAggregator = alphaAggregator;
        this.maxAlphaAdjustment = maxAlphaAdjustment;
    }

    @Override
    public BigDecimal calculateOffset(String symbol, boolean isBid, SymbolBo symbolBo) {
        BigDecimal tickSize = symbolBo.getTickSize();
        BigDecimal baseOffset = baseOffsetTicks.multiply(tickSize);

        // Get current base token balance
        BalanceBo balance = AccountStore.getAccount(EXCHANGE).getBalance(symbolBo.getBaseTokenId());
        BigDecimal currentPosition = balance.getTotal();

        // Compute alpha-adjusted target position
        BigDecimal target = baseTargetPosition;
        if (alphaAggregator != null && maxAlphaAdjustment.compareTo(BigDecimal.ZERO) > 0) {
            double alpha = alphaAggregator.getAlpha(symbol, symbolBo);
            BigDecimal alphaOffset = maxAlphaAdjustment.multiply(BigDecimal.valueOf(alpha));
            target = baseTargetPosition.add(alphaOffset);

            if (log.isDebugEnabled()) {
                log.debug("[{}] Alpha-adjusted target: base={}, alpha={}, adjustment={}, adjustedTarget={}",
                        symbol, baseTargetPosition, alpha, alphaOffset, target);
            }
        }

        // Compute normalized position ratio, clamped to [-1, 1]
        BigDecimal positionRatio = currentPosition.subtract(target)
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
            log.debug("[{}] Inventory spread: position={}, target={}, ratio={}, {} offset={}",
                    symbol, currentPosition, target, positionRatio, isBid ? "bid" : "ask", result);
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

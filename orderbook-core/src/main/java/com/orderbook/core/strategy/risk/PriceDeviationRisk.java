package com.orderbook.core.strategy.risk;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 拒绝那些价格与参考市场价偏离过大的订单。
 * 必须在每次策略心跳（tick）之前设定好参考价格（例如来自最优买卖盘报价）。
 */
@Slf4j
public class PriceDeviationRisk implements RiskCheck {

    private final BigDecimal maxDeviationPercent;

    @Setter
    private BigDecimal referencePrice;

    public PriceDeviationRisk(BigDecimal maxDeviationPercent) {
        this.maxDeviationPercent = maxDeviationPercent;
    }

    @Override
    public boolean check(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        if (referencePrice == null || price == null || referencePrice.compareTo(BigDecimal.ZERO) == 0) {
            return true; // no reference to compare against
        }

        BigDecimal deviation = price.subtract(referencePrice).abs()
                .divide(referencePrice, 6, RoundingMode.HALF_UP);

        if (deviation.compareTo(maxDeviationPercent) > 0) {
            log.warn("[{}] Price deviation {}% exceeds max {}%: price={} refPrice={}",
                    symbol, deviation.multiply(BigDecimal.valueOf(100)),
                    maxDeviationPercent.multiply(BigDecimal.valueOf(100)),
                    price, referencePrice);
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "priceDeviation";
    }
}

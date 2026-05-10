package com.orderbook.core.strategy.risk;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限制每个交易对（Symbol）的最大活动（未成交）订单数量。
 * 当前计数必须由外部更新（例如来自 OpenOrdersStore）。
 */
@Slf4j
public class MaxOrderCountRisk implements RiskCheck {

    private final int maxOrders;
    private final AtomicInteger currentCount = new AtomicInteger(0);

    public MaxOrderCountRisk(int maxOrders) {
        this.maxOrders = maxOrders;
    }

    /** 更新当前活动订单的计数。在每次策略心跳（tick）前调用。 */
    public void setCurrentCount(int count) {
        currentCount.set(count);
    }

    @Override
    public boolean check(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        int count = currentCount.get();
        if (count >= maxOrders) {
            log.warn("[{}] Max order count reached: {} >= {}, blocking {} order",
                    symbol, count, maxOrders, side);
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "maxOrderCount";
    }
}

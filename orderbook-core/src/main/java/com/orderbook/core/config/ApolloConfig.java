package com.orderbook.core.config;

import com.ctrip.framework.apollo.ConfigService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Data
@Component
public class ApolloConfig {

    /**
     * 日志开关
     * Params: symbol
     * Returns:
     */
    public boolean isMMLogSwitch(String symbol) {
        return ConfigService.getAppConfig().getBooleanProperty("log." + symbol + ".switch", false);
    }

    /**
     * 批量最大下单数量
     * Returns:
     */
    public int getMaxPlaceOrderLimit() {
        return ConfigService.getAppConfig().getIntProperty("max.place.order.limit", 10);
    }

    /**
     * 批量最大撤单数量
     * Returns:
     */
    public int getMaxCancelOrderLimit() {
        return ConfigService.getAppConfig().getIntProperty("max.cancel.order.limit", 40);
    }

    /**
     * 批量下单间隔时间
     * Returns:
     */
    public int getPlaceSleepTime() {
        return ConfigService.getAppConfig().getIntProperty("place.sleep.time.milliseconds", 50);
    }

    /**
     * 下单数量的限制
     * Returns:
     */
    public int getActiveOrderNumberLimit() {
        return ConfigService.getAppConfig().getIntProperty("active.orders.number.limit", 1500);
    }

    /**
     * fill redis timeout
     * Returns:
     */
    public static int getFillRedisTimeOut() {
        return ConfigService.getAppConfig().getIntProperty("fill.redis.timeout", 1500);
    }

    public int getOrderBookLimitLevel() {
        return ConfigService.getAppConfig().getIntProperty("order.book.level.limit", 200);
    }

    /** Circuit breaker: consecutive failures before opening. */
    public int getCircuitBreakerThreshold() {
        return ConfigService.getAppConfig().getIntProperty("risk.circuit.breaker.threshold", 10);
    }

    /** Circuit breaker: cooldown in milliseconds before reset. */
    public long getCircuitBreakerCooldownMs() {
        return ConfigService.getAppConfig().getLongProperty("risk.circuit.breaker.cooldown.ms", 60000L);
    }

    /** Price deviation: max percent deviation from reference price (e.g. 5.0 = 5%). */
    public double getPriceDeviationPercent() {
        return ConfigService.getAppConfig().getDoubleProperty("risk.price.deviation.percent", 5.0);
    }

    /** Max drawdown percent before blocking trades (e.g. 10.0 = 10%). */
    public double getMaxDrawdownPercent() {
        return ConfigService.getAppConfig().getDoubleProperty("risk.max.drawdown.percent", 10.0);
    }
}
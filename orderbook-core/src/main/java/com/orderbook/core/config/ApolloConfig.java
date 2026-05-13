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

    /** Rate limiter: max batch place requests per second. */
    public double getPlaceRateLimit() {
        return ConfigService.getAppConfig().getDoubleProperty("rate.limit.place.per.second", 10.0);
    }

    /** Rate limiter: max batch cancel requests per second. */
    public double getCancelRateLimit() {
        return ConfigService.getAppConfig().getDoubleProperty("rate.limit.cancel.per.second", 20.0);
    }

    // ---- Arbitrage config ----

    /** Cross-exchange arbitrage master switch. */
    public boolean getArbitrageEnabled() {
        return ConfigService.getAppConfig().getBooleanProperty("arbitrage.enabled", true);
    }

    /** Minimum arbitrage profit in USDT to consider an opportunity executable. */
    public double getArbitrageMinProfitUsdt() {
        return ConfigService.getAppConfig().getDoubleProperty("arbitrage.minProfitUsdt", 0.5);
    }

    /** Maximum order quantity for arbitrage trades. */
    public int getArbitrageMaxOrderQty() {
        return ConfigService.getAppConfig().getIntProperty("arbitrage.maxOrderQty", 1);
    }

    // ---- Portfolio risk config ----

    /** Maximum concentration per symbol (e.g. 0.30 = 30%). */
    public double getPortfolioConcentrationLimit() {
        return ConfigService.getAppConfig().getDoubleProperty("risk.portfolio.concentration.limit", 0.30);
    }

    /** Number of periods for VaR computation. */
    public int getPortfolioVaRPeriods() {
        return ConfigService.getAppConfig().getIntProperty("risk.portfolio.var.periods", 20);
    }

    /** Portfolio risk refresh interval in milliseconds. */
    public long getPortfolioRefreshIntervalMs() {
        return ConfigService.getAppConfig().getLongProperty("risk.portfolio.refresh.interval.ms", 5000L);
    }

    // ---- ML training config ----

    /** Auto-training interval in hours. */
    public int getMLAutoTrainIntervalHours() {
        return ConfigService.getAppConfig().getIntProperty("ml.auto.train.interval.hours", 1);
    }

    /** Maximum training data samples per symbol. */
    public int getMLTrainingDataMaxSamples() {
        return ConfigService.getAppConfig().getIntProperty("ml.training.data.max.samples", 10000);
    }

    /** Label lookahead periods for training data. */
    public int getMLLabelLookaheadPeriods() {
        return ConfigService.getAppConfig().getIntProperty("ml.label.lookahead.periods", 5);
    }

    /** Feature capture interval in strategy ticks. */
    public int getMLFeatureCaptureInterval() {
        return ConfigService.getAppConfig().getIntProperty("ml.feature.capture.interval.ticks", 5);
    }

    // ---- SOR config ----

    /** Smart Order Routing master switch. */
    public boolean getSOREnabled() {
        return ConfigService.getAppConfig().getBooleanProperty("sor.enabled", false);
    }

    /** SOR routing strategy. */
    public String getSORStrategy() {
        return ConfigService.getAppConfig().getProperty("sor.strategy", "lowest_fee");
    }

    /** SOR primary exchange. */
    public String getSORPrimaryExchange() {
        return ConfigService.getAppConfig().getProperty("sor.primary.exchange", "OSL_GLOBAL");
    }

    /** SOR fallback exchanges (comma-separated). */
    public String getSORFallbackExchanges() {
        return ConfigService.getAppConfig().getProperty("sor.fallback.exchanges", "");
    }

    /** SOR position weight for WEIGHTED strategy (0.0 ~ 1.0). */
    public double getSORPositionWeight() {
        return ConfigService.getAppConfig().getDoubleProperty("sor.position.weight", 0.2);
    }

    // ---- Event-driven strategy trigger config ----

    /** Mid-price must move at least N ticks to trigger strategy execution. */
    public int getStrategyTriggerTicks() {
        return ConfigService.getAppConfig().getIntProperty("strategy.trigger.ticks", 2);
    }

    /** Minimum interval between event-triggered executions (milliseconds). */
    public long getStrategyTriggerMinIntervalMs() {
        return ConfigService.getAppConfig().getLongProperty("strategy.trigger.minIntervalMs", 200L);
    }

    /** Fallback timer interval in milliseconds. */
    public long getStrategyFallbackIntervalMs() {
        return ConfigService.getAppConfig().getLongProperty("strategy.fallback.interval.ms", 5000L);
    }
}
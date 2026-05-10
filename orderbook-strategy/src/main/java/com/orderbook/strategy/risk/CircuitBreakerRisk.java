package com.orderbook.strategy.risk;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * 熔断器风险检查。
 * 在连续失败 N 次后，将在冷却期内拦截所有请求。
 * 在操作成功或冷却期结束后重置。
 */
@Slf4j
public class CircuitBreakerRisk implements RiskCheck {

    private final int threshold;
    private final long cooldownMs;

    @Getter
    private int consecutiveFailures;

    private long lastFailureTime;
    private boolean open;

    public CircuitBreakerRisk(int threshold, long cooldownMs) {
        this.threshold = threshold;
        this.cooldownMs = cooldownMs;
        this.consecutiveFailures = 0;
        this.open = false;
        this.lastFailureTime = 0;
    }

    @Override
    public boolean check(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        if (open) {
            if (System.currentTimeMillis() - lastFailureTime > cooldownMs) {
                log.warn("[{}] Circuit breaker reset after cooldown", symbol);
                open = false;
                consecutiveFailures = 0;
                return true;
            }
            log.warn("[{}] Circuit breaker open, blocking order. {} consecutive failures", symbol, consecutiveFailures);
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "circuitBreaker";
    }

    /** 记录一次成功的操作。重置失败计数。 */
    public void recordSuccess() {
        if (consecutiveFailures > 0) {
            consecutiveFailures = 0;
            open = false;
        }
    }

    /** 记录一次失败的操作。如果超过阈值，则打开熔断器（切断服务）。 */
    public void recordFailure(String symbol) {
        consecutiveFailures++;
        lastFailureTime = System.currentTimeMillis();
        if (consecutiveFailures >= threshold) {
            open = true;
            log.warn("[{}] Circuit breaker OPEN after {} consecutive failures", symbol, consecutiveFailures);
        }
    }
}

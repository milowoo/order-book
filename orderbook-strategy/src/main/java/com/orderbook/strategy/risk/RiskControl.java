package com.orderbook.strategy.risk;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 用于风险检查的责任链。
 * 通过下单前校验和熔断器机制，对策略执行进行封装（包裹）。
 */
@Slf4j
public class RiskControl {

    private final List<RiskCheck> preOrderChecks = new ArrayList<>();
    private final CircuitBreakerRisk circuitBreaker;

    public RiskControl(CircuitBreakerRisk circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        if (circuitBreaker != null) {
            preOrderChecks.add(circuitBreaker);
        }
    }

    /** Add a risk check to the chain. */
    public void addCheck(RiskCheck check) {
        if (check != null) {
            preOrderChecks.add(check);
        }
    }

    /**
     * 检查订单是否可以下单。
     * 所有检查项都必须通过；如果有任何一项检查失败，订单将被拒绝。
     */
    public boolean canPlaceOrder(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        for (RiskCheck check : preOrderChecks) {
            if (!check.check(symbol, side, price, quantity)) {
                log.warn("[{}] Risk check '{}' blocked {} order at price={} qty={}",
                        symbol, check.getName(), side, price, quantity);
                return false;
            }
        }
        return true;
    }

    /** 记录一次成功的操作（重置熔断器） */
    public void recordSuccess(String symbol) {
        if (circuitBreaker != null) {
            circuitBreaker.recordSuccess();
        }
    }

    /** Record a failed operation (may trigger circuit breaker). */
    public void recordFailure(String symbol) {
        if (circuitBreaker != null) {
            circuitBreaker.recordFailure(symbol);
        }
    }

    /** Check if the circuit breaker is currently open (blocking orders). */
    public boolean isCircuitBroken() {
        return circuitBreaker != null && !circuitBreaker.check("", "", BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Get the circuit breaker for inspection. */
    public CircuitBreakerRisk getCircuitBreaker() {
        return circuitBreaker;
    }

    /** Get all registered checks (for inspection/update by strategies). */
    public List<RiskCheck> getChecks() {
        return preOrderChecks;
    }
}

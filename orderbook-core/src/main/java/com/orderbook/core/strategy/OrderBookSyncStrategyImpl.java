package com.orderbook.core.strategy;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.cmd.order.CancelOrderBookOutOrderImpl;
import com.orderbook.core.cmd.order.OrdersMakerImpl;
import com.orderbook.core.config.ApolloConfig;
import com.orderbook.core.config.StrategyProps;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OpenOrdersStore;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.store.PriceStore;
import com.orderbook.core.strategy.risk.MaxDrawdownRisk;
import com.orderbook.core.strategy.spread.VolatilityTracker;
import com.orderbook.strategy.Strategy;
import com.orderbook.strategy.risk.CircuitBreakerRisk;
import com.orderbook.strategy.risk.MaxOrderCountRisk;
import com.orderbook.strategy.risk.PriceDeviationRisk;
import com.orderbook.strategy.risk.RiskControl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 替换主要的 Aviator 策略流程：
 cancel_order_book_out_order（取消订单簿之外的订单）
 orders_maker（与参考订单簿同步订单）
 包含风控措施：熔断器、最大订单数限制、价格偏离检查、最大回撤控制。
 记录中间价，用于波动率追踪（供价差计算器使用）。
 */
@Slf4j
@Component
public class OrderBookSyncStrategyImpl implements Strategy {

    @Autowired
    private CancelOrderBookOutOrderImpl cancelOrderBookOutOrder;

    @Autowired
    private OrdersMakerImpl ordersMaker;

    @Autowired
    private StrategyProps strategyProps;

    @Autowired
    private OpenOrdersStore openOrdersStore;

    @Autowired
    private OrderBookStore orderBookStore;

    @Autowired
    private ApolloConfig apolloConfig;

    @Autowired
    private PriceStore priceStore;

    @Autowired
    private VolatilityTracker volatilityTracker;

    private final Map<String, RiskControl> riskControls = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "orderBookSync";
    }

    @Override
    public void execute(String symbol, Map<String, String> context) {
        SymbolBo symbolBo = strategyProps.findSymbol(symbol);
        if (symbolBo == null) {
            log.warn("[{}] Symbol not found in strategy props, skipping", symbol);
            return;
        }

        // Record mid-price for volatility tracking (used by spread calculators)
        //记录中间价以用于波动率追踪（供价差计算器使用）。
        recordMidPrice(symbol, symbolBo);

        RiskControl rc = getOrCreateRiskControl(symbol, symbolBo);
        if (rc.isCircuitBroken()) {
            log.warn("[{}] Circuit breaker open, skipping OrderBookSyncStrategy", symbol);
            return;
        }

        // Check max drawdown before proceeding 在继续执行之前，先检查最大回撤
        if (isMaxDrawdownExceeded(rc)) {
            log.warn("[{}] Max drawdown exceeded, skipping OrderBookSyncStrategy", symbol);
            return;
        }

        Map<String, Object> env = createEnv(symbol, symbolBo, context);
        ExchangeCode exchange = ExchangeCode.OSL_GLOBAL;

        try {
            // Step 1: Cancel orders outside the order book range
            cancelOrderBookOutOrder.call(env, exchange, symbol);

            // Step 2: Update order count for MaxOrderCountRisk
            updateOrderCount(symbol, symbolBo);

            // Step 3: Update MaxDrawdownRisk state
            updateMaxDrawdownRisk(symbol, symbolBo);

            // Step 4: Sync orders with reference order book
            boolean success = ordersMaker.call(env, exchange, symbol);

            if (success) {
                rc.recordSuccess(symbol);
            } else {
                rc.recordFailure(symbol);
            }
        } catch (Exception e) {
            log.error("[{}] OrderBookSyncStrategy execution failed", symbol, e);
            rc.recordFailure(symbol);
        }
    }

    /** Record mid-price for the VolatilityTracker. */
    private void recordMidPrice(String symbol, SymbolBo symbolBo) {
        try {
            OrderBook bybitBook = orderBookStore.get(ExchangeCode.BYBIT, symbolBo.getSymbolId());
            if (bybitBook == null || bybitBook.getBid().isEmpty() || bybitBook.getAsk().isEmpty()) return;
            BigDecimal mid = bybitBook.getBestBidPrice()
                    .add(bybitBook.getBestAskPrice())
                    .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            volatilityTracker.recordPrice(symbol, mid);
        } catch (Exception e) {
            log.warn("[{}] Failed to record mid price for volatility", symbol, e);
        }
    }

    /** Check if max drawdown risk is triggered. */
    private boolean isMaxDrawdownExceeded(RiskControl rc) {
        return rc.getChecks().stream()
                .filter(c -> c instanceof MaxDrawdownRisk)
                .anyMatch(c -> !c.check("", "", BigDecimal.ZERO, BigDecimal.ZERO));
    }

    /** Update MaxDrawdownRisk with current portfolio value. */
    private void updateMaxDrawdownRisk(String symbol, SymbolBo symbolBo) {
        RiskControl rc = riskControls.get(symbol);
        if (rc == null) return;
        rc.getChecks().stream()
                .filter(c -> c instanceof MaxDrawdownRisk)
                .map(c -> (MaxDrawdownRisk) c)
                .findFirst()
                .ifPresent(risk -> risk.update(symbolBo));
    }

    private void updateOrderCount(String symbol, SymbolBo symbolBo) {
        RiskControl rc = riskControls.get(symbol);
        if (rc == null) return;

        rc.getChecks().stream()
                .filter(c -> c instanceof MaxOrderCountRisk)
                .map(c -> (MaxOrderCountRisk) c)
                .findFirst()
                .ifPresent(countRisk -> {
                    try {
                        int count = openOrdersStore.getRemoteOpenOrders(symbolBo).getOrders().size();
                        countRisk.setCurrentCount(count);
                    } catch (Exception e) {
                        log.warn("[{}] Failed to get open orders count for risk control", symbol, e);
                    }
                });
    }

    private RiskControl getOrCreateRiskControl(String symbol, SymbolBo symbolBo) {
        return riskControls.computeIfAbsent(symbol, s -> {
            CircuitBreakerRisk cb = new CircuitBreakerRisk(
                    apolloConfig.getCircuitBreakerThreshold(),
                    apolloConfig.getCircuitBreakerCooldownMs()
            );
            RiskControl rc = new RiskControl(cb);

            // Max order count check
            MaxOrderCountRisk maxOrderRisk = new MaxOrderCountRisk(
                    apolloConfig.getActiveOrderNumberLimit()
            );
            rc.addCheck(maxOrderRisk);

            // Price deviation check
            PriceDeviationRisk priceRisk = new PriceDeviationRisk(
                    BigDecimal.valueOf(apolloConfig.getPriceDeviationPercent())
            );
            rc.addCheck(priceRisk);

            // Max drawdown risk
            MaxDrawdownRisk drawdownRisk = new MaxDrawdownRisk(
                    BigDecimal.valueOf(apolloConfig.getMaxDrawdownPercent()),
                    orderBookStore,
                    priceStore
            );
            rc.addCheck(drawdownRisk);

            log.info("[{}] Created RiskControl: maxOrders={}, priceDev={}%, drawdown={}%, cbThreshold={}, cbCooldown={}ms",
                    s, apolloConfig.getActiveOrderNumberLimit(),
                    apolloConfig.getPriceDeviationPercent(),
                    apolloConfig.getMaxDrawdownPercent(),
                    apolloConfig.getCircuitBreakerThreshold(),
                    apolloConfig.getCircuitBreakerCooldownMs());

            return rc;
        });
    }

    private Map<String, Object> createEnv(String symbol, SymbolBo symbolBo, Map<String, String> context) {
        Map<String, Object> env = new HashMap<>();
        env.put("symbol", symbol);
        env.put("exchange", "OSL_GLOBAL");
        if (context != null) {
            env.put("context", context);
        }
        if (symbolBo.getOthersProps() != null) {
            env.putAll(symbolBo.getOthersProps());
        }
        return env;
    }
}

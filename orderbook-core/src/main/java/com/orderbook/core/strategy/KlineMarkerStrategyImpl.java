package com.orderbook.core.strategy;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.cmd.order.KlineCancelOrderImpl;
import com.orderbook.core.cmd.order.KlineMakerImpl;
import com.orderbook.core.config.ApolloConfig;
import com.orderbook.core.config.StrategyProps;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.strategy.Strategy;
import com.orderbook.strategy.risk.CircuitBreakerRisk;
import com.orderbook.strategy.risk.PriceDeviationRisk;
import com.orderbook.strategy.risk.RiskControl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 替换原有的 Aviator K线标记流（流程）：
 kline_maker（下达标记订单）
 kline_cancel_order（清理旧的 K线订单）
 包含风控措施：熔断器和价格偏离检查。
 */
@Slf4j
@Component
public class KlineMarkerStrategyImpl implements Strategy {

    @Autowired
    private KlineMakerImpl klineMaker;

    @Autowired
    private KlineCancelOrderImpl klineCancelOrder;

    @Autowired
    private StrategyProps strategyProps;

    @Autowired
    private ApolloConfig apolloConfig;

    private final Map<String, RiskControl> riskControls = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "klineMarker";
    }

    @Override
    public void execute(String symbol, Map<String, String> context) {
        SymbolBo symbolBo = strategyProps.findSymbol(symbol);
        if (symbolBo == null) {
            log.warn("[{}] Symbol not found in strategy props, skipping", symbol);
            return;
        }

        RiskControl rc = getOrCreateRiskControl(symbol);
        if (rc.isCircuitBroken()) {
            log.warn("[{}] Circuit breaker open, skipping KlineMarkerStrategy", symbol);
            return;
        }

        Map<String, Object> env = createEnv(symbol, symbolBo, context);
        ExchangeCode exchange = ExchangeCode.OSL_GLOBAL;

        try {
            // Step 1: Place K-line marker orders
            klineMaker.call(env, exchange, symbol);

            // Step 2: Clean up old K-line orders to avoid order pileup
            klineCancelOrder.call(env, exchange, symbol);

            rc.recordSuccess(symbol);
        } catch (Exception e) {
            log.error("[{}] KlineMarkerStrategy execution failed", symbol, e);
            rc.recordFailure(symbol);
        }
    }

    private RiskControl getOrCreateRiskControl(String symbol) {
        return riskControls.computeIfAbsent(symbol, s -> {
            CircuitBreakerRisk cb = new CircuitBreakerRisk(
                    apolloConfig.getCircuitBreakerThreshold(),
                    apolloConfig.getCircuitBreakerCooldownMs()
            );
            RiskControl rc = new RiskControl(cb);

            PriceDeviationRisk priceRisk = new PriceDeviationRisk(
                    BigDecimal.valueOf(apolloConfig.getPriceDeviationPercent())
            );
            rc.addCheck(priceRisk);

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

package com.orderbook.core.sor;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.config.ApolloConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能订单路由（SOR）引擎。
 * 根据可配置的策略，选择最佳的交易所进行下单。
 */
@Slf4j
@Service
public class SOREngine {

    private final RoutingTable routingTable;
    private final ApolloConfig apolloConfig;

    public SOREngine(RoutingTable routingTable, ApolloConfig apolloConfig) {
        this.routingTable = routingTable;
        this.apolloConfig = apolloConfig;
    }

    /**
     * 选择最佳的交易所进行下单。
     */
    public ExchangeCode selectExchange(String symbol, String side,
                                        BigDecimal price, BigDecimal qty) {
        if (!apolloConfig.getSOREnabled()) {
            return getPrimaryExchange();
        }

        List<ExchangeCode> ranked = rankExchanges(symbol);
        if (ranked.isEmpty()) {
            log.warn("[SOR] No healthy exchanges available for {}, falling back to primary", symbol);
            return getPrimaryExchange();
        }

        ExchangeCode selected = ranked.get(0);
        if (log.isDebugEnabled()) {
            log.debug("[SOR] Selected {} for {} {} (ranked: {})", selected, symbol, side, ranked);
        }
        return selected;
    }

    /**
     *  当主交易所失败时，选择备用的交易所。
     */
    public ExchangeCode selectFallback(ExchangeCode failedExchange) {
        if (!apolloConfig.getSOREnabled()) {
            return getPrimaryExchange();
        }

        List<ExchangeCode> healthy = routingTable.getHealthyExchanges();
        healthy.remove(failedExchange);

        if (healthy.isEmpty()) {
            return getPrimaryExchange();
        }

        return healthy.get(0);
    }

    /**
     * 根据配置的策略，对所有健康的交易所进行排名。
     */
    public List<ExchangeCode> rankExchanges(String symbol) {
        RoutingStrategy strategy = getRoutingStrategy();
        List<ExchangeStats> allStats = routingTable.getAllStats();

        List<ExchangeStats> healthyStats = allStats.stream()
                .filter(ExchangeStats::isHealthy)
                .collect(Collectors.toList());

        if (healthyStats.isEmpty()) return List.of();

        Comparator<ExchangeStats> comparator;
        switch (strategy) {
            case LOWEST_FEE:
                comparator = Comparator.comparingDouble(
                        s -> s.getTakerFeeRate().doubleValue());
                break;
            case FASTEST:
                comparator = Comparator.comparingDouble(ExchangeStats::getAvgLatencyMicros);
                break;
            case BEST_LIQUIDITY:
                comparator = Comparator.comparingDouble(
                        ExchangeStats::getFillProbabilityProxy).reversed();
                break;
            case WEIGHTED:
                comparator = (a, b) -> Double.compare(weightedScore(b), weightedScore(a));
                break;
            default:
                comparator = Comparator.comparingDouble(
                        s -> s.getTakerFeeRate().doubleValue());
        }

        return healthyStats.stream()
                .sorted(comparator)
                .map(ExchangeStats::getExchange)
                .collect(Collectors.toList());
    }

    /**
     * 结合了费率、延迟和流动性的加权得分（每项指标均为 0-1 范围）。
     */
    private double weightedScore(ExchangeStats stats) {
        double feeScore = 1.0 / (1.0 + stats.getTakerFeeRate().doubleValue() * 1000);
        double latScore = 1.0 / (1.0 + stats.getAvgLatencyMicros() / 1000.0);
        double liqScore = stats.getFillProbabilityProxy();
        return 0.4 * feeScore + 0.3 * latScore + 0.3 * liqScore;
    }

    private ExchangeCode getPrimaryExchange() {
        try {
            return ExchangeCode.valueOf(apolloConfig.getSORPrimaryExchange());
        } catch (Exception e) {
            return ExchangeCode.OSL_GLOBAL;
        }
    }

    private RoutingStrategy getRoutingStrategy() {
        try {
            return RoutingStrategy.valueOf(
                    apolloConfig.getSORStrategy().toUpperCase());
        } catch (Exception e) {
            return RoutingStrategy.LOWEST_FEE;
        }
    }
}

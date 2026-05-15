package com.orderbook.core.arbitrage;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.global.GlobalExchange;
import com.orderbook.connector.global.service.GlobalTradeService;
import com.orderbook.connector.interfaces.ConnectorFactory;
import com.orderbook.core.config.ApolloConfig;
import com.orderbook.core.config.SerialExecutorManager;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.SymbolStore;
import com.orderbook.core.util.RateLimiterManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * Places small taker orders on OSL_GLOBAL to directly capture
 * one leg of an arbitrage opportunity. The existing market making
 * orders on the opposite side act as the natural hedge.
 * <p>
 * This is the "hybrid mode" — market making + selective taker execution.
 * Only fires for high-confidence opportunities (OSL_GLOBAL involved,
 * net profit above configurable threshold).
 */
@Slf4j
@Service
public class ArbitrageTaker {

    private final ConnectorFactory connectorFactory;
    private final ApolloConfig apolloConfig;
    private final SymbolStore symbolStore;
    private final RateLimiterManager rateLimiterManager;

    // Dedicated serial executor per symbol for taker orders
    private final SerialExecutorManager serialExecutorManager = new SerialExecutorManager();

    // Staleness threshold: skip opportunity if detected more than this many ms ago
    private static final long MAX_OPPORTUNITY_AGE_MS = 3_000L;

    public ArbitrageTaker(ConnectorFactory connectorFactory,
                          ApolloConfig apolloConfig,
                          SymbolStore symbolStore,
                          RateLimiterManager rateLimiterManager) {
        this.connectorFactory = connectorFactory;
        this.apolloConfig = apolloConfig;
        this.symbolStore = symbolStore;
        this.rateLimiterManager = rateLimiterManager;
    }

    /**
     * Execute a taker order if the opportunity qualifies.
     * Safe to call from any thread — execution is serialized per symbol.
     */
    public void executeIfProfitable(ArbitrageOpportunity opportunity) {
        if (!apolloConfig.getArbitrageTakerEnabled()) return;
        if (opportunity == null || !opportunity.isExecutable()) return;

        // Staleness check: don't chase stale prices
        long age = System.currentTimeMillis() - opportunity.getDetectedAt();
        if (age > MAX_OPPORTUNITY_AGE_MS) {
            log.debug("[ArbitrageTaker] Opportunity too old ({}ms), skipping", age);
            return;
        }

        BigDecimal minProfit = BigDecimal.valueOf(apolloConfig.getArbitrageTakerMinProfitUsdt());
        if (opportunity.getNetProfit().compareTo(minProfit) < 0) return;

        SymbolBo symbolBo = symbolStore.findSymbolById(opportunity.getSymbol());
        if (symbolBo == null) {
            log.warn("[ArbitrageTaker] SymbolBo not found for {}", opportunity.getSymbol());
            return;
        }

        // OSL_GLOBAL is the cheaper exchange → buy on OSL
        if (opportunity.getBuyExchange() == ExchangeCode.OSL_GLOBAL) {
            executeTaker(opportunity, symbolBo, "buy",
                    opportunity.getBuyPrice(), opportunity.getMaxQuantity());

        // OSL_GLOBAL is the more expensive exchange → sell on OSL
        } else if (opportunity.getSellExchange() == ExchangeCode.OSL_GLOBAL) {
            executeTaker(opportunity, symbolBo, "sell",
                    opportunity.getSellPrice(), opportunity.getMaxQuantity());
        }
    }

    private void executeTaker(ArbitrageOpportunity opp, SymbolBo symbolBo,
                              String side, BigDecimal price, BigDecimal maxQty) {
        // Cap quantity by config
        int configMaxQty = apolloConfig.getArbitrageTakerMaxQty();
        BigDecimal finalQty = maxQty.min(BigDecimal.valueOf(configMaxQty));
        if (finalQty.compareTo(BigDecimal.ZERO) <= 0) return;

        Runnable task = () -> {
            try {
                GlobalExchange exchange = (GlobalExchange) connectorFactory.getTradingExchange(
                        ExchangeCode.OSL_GLOBAL,
                        symbolBo.getApiKey(),
                        symbolBo.getSecretKey(),
                        symbolBo.getPassword()
                );
                GlobalTradeService tradeService = (GlobalTradeService) exchange.getTradeService();

                // Use market order for guaranteed execution at small size
                CurrencyPair currencyPair = new CurrencyPair(
                        symbolBo.getBaseTokenId(), symbolBo.getQuoteTokenId());
                Order.OrderType orderType = "buy".equalsIgnoreCase(side)
                        ? Order.OrderType.BID : Order.OrderType.ASK;
                MarketOrder marketOrder = new MarketOrder(orderType, finalQty, currencyPair);

                rateLimiterManager.acquirePlace();
                long start = System.currentTimeMillis();
                String orderId = tradeService.placeMarketOrder(marketOrder);
                long elapsed = System.currentTimeMillis() - start;

                log.info("[ArbitrageTaker] {} market-{} {} qty={} price={} orderId={} cost={}ms",
                        opp.getSymbol(), side, ExchangeCode.OSL_GLOBAL,
                        finalQty, price, orderId, elapsed);

            } catch (Exception e) {
                log.error("[ArbitrageTaker] {} market-{} failed: {}",
                        opp.getSymbol(), side, e.getMessage());
            }
        };

        // Serialize per symbol to avoid race conditions
        serialExecutorManager.submit(symbolBo.getSymbolId(), "arb_" + side, task);
    }

    @PreDestroy
    public void destroy() {
        serialExecutorManager.shutdownAll();
    }
}

package com.orderbook.core.strategy.event;

import com.lmax.disruptor.EventHandler;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.component.StrategyExecutor;
import com.orderbook.core.config.ApolloConfig;
import com.orderbook.core.config.StrategyProps;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.SymbolBo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LMAX Disruptor event handler that detects mid-price movements on the reference
 * exchange (BYBIT) and triggers event-driven strategy execution.
 *
 * When the mid-price moves by more than {@code triggerTicks × tickSize},
 * calls {@link StrategyExecutor#triggerNow(String)} to wake up the strategy thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceMovementDetector implements EventHandler<OrderBook> {

    private static final BigDecimal DEFAULT_TICK_SIZE = new BigDecimal("0.0001");

    private final StrategyExecutor strategyExecutor;
    private final StrategyProps strategyProps;
    private final ApolloConfig apolloConfig;

    /** Per-symbol last mid-price. */
    private final Map<String, BigDecimal> lastMidPrices = new ConcurrentHashMap<>();

    @Override
    public void onEvent(OrderBook event, long sequence, boolean endOfBatch) {
        // Only watch BYBIT — the reference exchange for mid-price
        if (event.getExchange() != ExchangeCode.BYBIT) return;
        if (event.getBid() == null || event.getBid().isEmpty()
                || event.getAsk() == null || event.getAsk().isEmpty()) return;

        BigDecimal bestBid = event.getBestBidPrice();
        BigDecimal bestAsk = event.getBestAskPrice();
        if (bestBid == null || bestAsk == null
                || bestBid.compareTo(BigDecimal.ZERO) == 0
                || bestAsk.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal mid = bestBid.add(bestAsk)
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);

        String symbol = event.getSymbol();
        BigDecimal prev = lastMidPrices.get(symbol);

        // Compute threshold = triggerTicks * tickSize
        BigDecimal tickSize = resolveTickSize(convertSymbol(symbol));
        BigDecimal threshold = tickSize.multiply(
                BigDecimal.valueOf(apolloConfig.getStrategyTriggerTicks()));

        boolean shouldTrigger = (prev == null)
                || mid.subtract(prev).abs().compareTo(threshold) >= 0;

        if (shouldTrigger) {
            lastMidPrices.put(symbol, mid);
            strategyExecutor.triggerNow(convertSymbol(symbol));
        }
    }

    /** Convert exchange symbol format (e.g. "BTC/USDT") to internal format (e.g. "BTCUSDT"). */
    private static String convertSymbol(String exchangeSymbol) {
        if (exchangeSymbol == null) return null;
        return exchangeSymbol.replace("/", "");
    }

    private BigDecimal resolveTickSize(String symbol) {
        SymbolBo bo = strategyProps.findSymbol(symbol);
        if (bo != null && bo.getTickSize() != null
                && bo.getTickSize().compareTo(BigDecimal.ZERO) > 0) {
            return bo.getTickSize();
        }
        return DEFAULT_TICK_SIZE;
    }
}

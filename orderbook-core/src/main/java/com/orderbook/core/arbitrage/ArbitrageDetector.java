package com.orderbook.core.arbitrage;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.service.FeeService;
import com.orderbook.core.store.OrderBookStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans order books across exchanges and detects arbitrage opportunities.
 * Compares best bid/ask prices between every pair of configured exchanges.
 */
@Slf4j
@Component
public class ArbitrageDetector {

    private final OrderBookStore orderBookStore;
    private final FeeService feeService;

    // Exchanges to scan in each cycle
    private static final ExchangeCode[] SCAN_EXCHANGES = {
            ExchangeCode.BYBIT,
            ExchangeCode.BINANCE,
            ExchangeCode.BITGET,
            ExchangeCode.OSL_GLOBAL
    };

    // OSL_GLOBAL is the only exchange we can execute on
    private static final ExchangeCode EXECUTION_EXCHANGE = ExchangeCode.OSL_GLOBAL;

    public ArbitrageDetector(OrderBookStore orderBookStore, FeeService feeService) {
        this.orderBookStore = orderBookStore;
        this.feeService = feeService;
    }

    /**
     * Scan for arbitrage opportunities across all exchange pairs.
     */
    public List<ArbitrageOpportunity> scan(String symbol, ArbitrageConfig config) {
        if (!config.isEnabled()) {
            return List.of();
        }

        List<ArbitrageOpportunity> opportunities = new ArrayList<>();

        for (int i = 0; i < SCAN_EXCHANGES.length; i++) {
            for (int j = 0; j < SCAN_EXCHANGES.length; j++) {
                if (i == j) continue;

                ExchangeCode exA = SCAN_EXCHANGES[i];
                ExchangeCode exB = SCAN_EXCHANGES[j];

                OrderBook bookA = orderBookStore.get(exA, symbol);
                OrderBook bookB = orderBookStore.get(exB, symbol);

                if (!isValid(bookA) || !isValid(bookB)) continue;

                // Direction: buy on A (at exA's best ask), sell on B (at exB's best bid)
                checkPair(bookA, bookB, exA, exB, symbol, config, opportunities);
            }
        }

        return opportunities;
    }

    private void checkPair(OrderBook bookA, OrderBook bookB,
                           ExchangeCode exA, ExchangeCode exB,
                           String symbol, ArbitrageConfig config,
                           List<ArbitrageOpportunity> opportunities) {
        BigDecimal buyPrice = bookA.getBestAskPrice();   // price to buy on exA
        BigDecimal sellPrice = bookB.getBestBidPrice();  // price to sell on exB

        if (buyPrice == null || sellPrice == null) return;
        if (buyPrice.compareTo(BigDecimal.ZERO) <= 0 || sellPrice.compareTo(BigDecimal.ZERO) <= 0) return;

        // Theoretical profit per unit
        BigDecimal theoreticalProfit = sellPrice.subtract(buyPrice);
        if (theoreticalProfit.compareTo(BigDecimal.ZERO) <= 0) return; // no profit

        // Calculate fees (taker on both sides)
        BigDecimal buyFee = feeService.estimateFee(exA, symbol, buyPrice);  // per unit fee
        BigDecimal sellFee = feeService.estimateFee(exB, symbol, sellPrice);
        BigDecimal totalFee = buyFee.add(sellFee);

        // Net profit per unit
        BigDecimal netProfit = theoreticalProfit.subtract(totalFee);

        // Minimum profit check
        if (netProfit.compareTo(config.getMinProfitUsdt()) < 0) return;

        // Calculate max quantity based on available depth
        BigDecimal bidQty = getTopLevelQty(bookB.getBid());
        BigDecimal askQty = getTopLevelQty(bookA.getAsk());
        BigDecimal maxQty = bidQty.min(askQty).min(BigDecimal.valueOf(config.getMaxOrderQty()));
        if (maxQty.compareTo(BigDecimal.ZERO) <= 0) return;

        boolean executable = exA == EXECUTION_EXCHANGE || exB == EXECUTION_EXCHANGE;

        ArbitrageOpportunity opp = new ArbitrageOpportunity(
                symbol, exA, exB, buyPrice, sellPrice,
                theoreticalProfit, totalFee, netProfit, maxQty, executable);

        opportunities.add(opp);

        if (log.isDebugEnabled()) {
            log.debug("[Arbitrage] {}: buy {} @ {} ({}), sell {} @ {} ({}), profit={}, executable={}",
                    symbol, exA, buyPrice, bookA.getAsk().get(0).getQuantity(),
                    exB, sellPrice, bookB.getBid().get(0).getQuantity(),
                    netProfit, executable);
        }
    }

    private boolean isValid(OrderBook book) {
        if (book == null) return false;
        if (book.getBid() == null || book.getBid().isEmpty()) return false;
        if (book.getAsk() == null || book.getAsk().isEmpty()) return false;
        return true;
    }

    private BigDecimal getTopLevelQty(List<com.orderbook.core.domain.PriceLevel> levels) {
        if (levels == null || levels.isEmpty()) return BigDecimal.ZERO;
        return levels.get(0).getQuantity();
    }
}

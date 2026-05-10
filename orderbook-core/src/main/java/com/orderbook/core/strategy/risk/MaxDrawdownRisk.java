package com.orderbook.core.strategy.risk;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.BalanceBo;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.AccountStore;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.store.PriceStore;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 最大回撤风险检查。
 追踪投资组合价值（基础资产数量 * 中间价 + 计价资产数量），
 如果从最高点的回撤幅度超过了配置的阈值，则拒绝交易。
 投资组合价值通过 {@link #update(SymbolBo)} 在每个心跳（tick）时更新。
 */
@Slf4j
public class MaxDrawdownRisk implements RiskCheck {

    private static final ExchangeCode BALANCE_EXCHANGE = ExchangeCode.OSL_GLOBAL;
    private static final ExchangeCode PRICE_EXCHANGE = ExchangeCode.BYBIT;

    private final BigDecimal maxDrawdownPercent;
    private final OrderBookStore orderBookStore;
    private final PriceStore priceStore;

    private volatile BigDecimal currentPortfolioValue = BigDecimal.ZERO;
    private volatile BigDecimal peakPortfolioValue = BigDecimal.ZERO;
    private volatile boolean initialized = false;

    public MaxDrawdownRisk(BigDecimal maxDrawdownPercent,
                           OrderBookStore orderBookStore,
                           PriceStore priceStore) {
        this.maxDrawdownPercent = maxDrawdownPercent;
        this.orderBookStore = orderBookStore;
        this.priceStore = priceStore;
    }

    /**
    更新投资组合价值并追踪最高峰值。
    在主下单流程之前，每个心跳（tick）调用一次。
    */
    public void update(SymbolBo symbolBo) {
        if (symbolBo == null) return;
        String symbol = symbolBo.getSymbol();

        try {
            BalanceBo baseBal = AccountStore.getAccount(BALANCE_EXCHANGE)
                    .getBalance(symbolBo.getBaseTokenId());
            BalanceBo quoteBal = AccountStore.getAccount(BALANCE_EXCHANGE)
                    .getBalance(symbolBo.getQuoteTokenId());

            // Get mid price from reference order book
            OrderBook refBook = orderBookStore.get(PRICE_EXCHANGE, symbolBo.getSymbolId());
            BigDecimal midPrice;
            if (refBook != null && !refBook.getBid().isEmpty() && !refBook.getAsk().isEmpty()) {
                midPrice = refBook.getBestBidPrice()
                        .add(refBook.getBestAskPrice())
                        .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            } else {
                // Fall back to last price from PriceStore
                BigDecimal lastPrice = priceStore.getLastPrice(PRICE_EXCHANGE, symbolBo.getSymbolId());
                if (lastPrice == null) return; // no price data available
                midPrice = lastPrice;
            }

            BigDecimal portfolioValue = baseBal.getTotal().multiply(midPrice).add(quoteBal.getTotal());
            currentPortfolioValue = portfolioValue;

            if (!initialized || portfolioValue.compareTo(peakPortfolioValue) > 0) {
                peakPortfolioValue = portfolioValue;
                initialized = true;
            }

            if (log.isDebugEnabled()) {
                BigDecimal drawdownPct = computeDrawdownPct();
                log.debug("[{}] MaxDrawdown: current={}, peak={}, drawdown={}%",
                        symbol, portfolioValue, peakPortfolioValue,
                        drawdownPct.multiply(BigDecimal.valueOf(100)));
            }
        } catch (Exception e) {
            log.warn("[{}] MaxDrawdownRisk.update failed: {}", symbol, e.getMessage());
        }
    }

    @Override
    public boolean check(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        if (!initialized || peakPortfolioValue.compareTo(BigDecimal.ZERO) == 0) {
            return true; // not enough data yet
        }

        BigDecimal drawdownPct = computeDrawdownPct();
        if (drawdownPct.compareTo(maxDrawdownPercent) > 0) {
            log.warn("[{}] Max drawdown exceeded: {}% > {}%, blocking trades",
                    symbol,
                    drawdownPct.multiply(BigDecimal.valueOf(100)),
                    maxDrawdownPercent.multiply(BigDecimal.valueOf(100)));
            return false;
        }
        return true;
    }

    private BigDecimal computeDrawdownPct() {
        if (peakPortfolioValue.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return peakPortfolioValue.subtract(currentPortfolioValue)
                .divide(peakPortfolioValue, 6, RoundingMode.HALF_UP);
    }

    @Override
    public String getName() {
        return "maxDrawdown";
    }
}

package com.orderbook.core.strategy.risk;

import com.orderbook.core.domain.SymbolBo;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Portfolio-level max drawdown risk check.
 * Delegates portfolio value computation to the shared {@link PortfolioRiskManager},
 * eliminating the double-counting bug from per-symbol USDT balance aggregation.
 */
@Slf4j
public class MaxDrawdownRisk implements RiskCheck {

    private final BigDecimal maxDrawdownPercent;
    private final PortfolioRiskManager portfolioRiskManager;

    public MaxDrawdownRisk(BigDecimal maxDrawdownPercent,
                           PortfolioRiskManager portfolioRiskManager) {
        this.maxDrawdownPercent = maxDrawdownPercent;
        this.portfolioRiskManager = portfolioRiskManager;
    }

    /**
     * Update is now a no-op. Portfolio values are computed by PortfolioRiskManager
     * on its own @Scheduled timer.
     */
    public void update(SymbolBo symbolBo) {
        // Delegated to PortfolioRiskManager.refresh()
    }

    @Override
    public boolean check(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        BigDecimal drawdownPct = portfolioRiskManager.getDrawdownPct();

        if (drawdownPct.compareTo(maxDrawdownPercent) > 0) {
            log.warn("[{}] Max drawdown exceeded: {}% > {}%, blocking trades",
                    symbol,
                    drawdownPct.multiply(BigDecimal.valueOf(100)),
                    maxDrawdownPercent.multiply(BigDecimal.valueOf(100)));
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "maxDrawdown";
    }
}

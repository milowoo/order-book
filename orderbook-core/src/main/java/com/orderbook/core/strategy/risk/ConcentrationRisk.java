package com.orderbook.core.strategy.risk;

import com.orderbook.core.config.ApolloConfig;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Risk check that rejects orders if a single symbol exceeds
 * the configured concentration limit (% of total portfolio value).
 */
@Slf4j
public class ConcentrationRisk implements RiskCheck {

    private final PortfolioRiskManager portfolioRiskManager;
    private final double maxConcentrationPct;

    public ConcentrationRisk(PortfolioRiskManager portfolioRiskManager, double maxConcentrationPct) {
        this.portfolioRiskManager = portfolioRiskManager;
        this.maxConcentrationPct = maxConcentrationPct;
    }

    @Override
    public boolean check(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        double concentration = portfolioRiskManager.getSymbolConcentration(symbol);
        if (concentration > maxConcentrationPct) {
            log.warn("[{}] Concentration risk: {}% > {}%, blocking order",
                    symbol,
                    String.format("%.1f", concentration * 100),
                    String.format("%.1f", maxConcentrationPct * 100));
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "concentration";
    }
}

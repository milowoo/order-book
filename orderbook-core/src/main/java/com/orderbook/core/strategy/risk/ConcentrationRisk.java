package com.orderbook.core.strategy.risk;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * 风险检查：如果单一交易标的的持仓超过了设定的集中度限制（占总投资组合价值的百分比），则拒绝下单。
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

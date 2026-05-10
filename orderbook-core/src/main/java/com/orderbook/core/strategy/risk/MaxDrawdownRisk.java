package com.orderbook.core.strategy.risk;

import com.orderbook.core.domain.SymbolBo;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * 投资组合级别的最大回撤风险检查。
 * 将投资组合价值的计算委托给共享的 {@link PortfolioRiskManager}，
 * 从而消除了因按交易标的聚合 USDT 余额而导致的重复计算错误。
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

package com.orderbook.core.arbitrage;

import com.orderbook.cmd.ExchangeCode;

import java.math.BigDecimal;

/**
 * A detected cross-exchange arbitrage opportunity.
 */
public class ArbitrageOpportunity {
    private final String symbol;
    private final ExchangeCode buyExchange;
    private final ExchangeCode sellExchange;
    private final BigDecimal buyPrice;
    private final BigDecimal sellPrice;
    private final BigDecimal theoreticalProfit;
    private final BigDecimal fee;
    private final BigDecimal netProfit;
    private final BigDecimal maxQuantity;
    private final long detectedAt;
    private final boolean executable;

    public ArbitrageOpportunity(String symbol, ExchangeCode buyExchange, ExchangeCode sellExchange,
                                BigDecimal buyPrice, BigDecimal sellPrice,
                                BigDecimal theoreticalProfit, BigDecimal fee, BigDecimal netProfit,
                                BigDecimal maxQuantity, boolean executable) {
        this.symbol = symbol;
        this.buyExchange = buyExchange;
        this.sellExchange = sellExchange;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.theoreticalProfit = theoreticalProfit;
        this.fee = fee;
        this.netProfit = netProfit;
        this.maxQuantity = maxQuantity;
        this.detectedAt = System.currentTimeMillis();
        this.executable = executable;
    }

    public String getSymbol() { return symbol; }
    public ExchangeCode getBuyExchange() { return buyExchange; }
    public ExchangeCode getSellExchange() { return sellExchange; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public BigDecimal getSellPrice() { return sellPrice; }
    public BigDecimal getTheoreticalProfit() { return theoreticalProfit; }
    public BigDecimal getFee() { return fee; }
    public BigDecimal getNetProfit() { return netProfit; }
    public BigDecimal getMaxQuantity() { return maxQuantity; }
    public long getDetectedAt() { return detectedAt; }
    public boolean isExecutable() { return executable; }
}

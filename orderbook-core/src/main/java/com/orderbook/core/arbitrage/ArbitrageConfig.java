package com.orderbook.core.arbitrage;

import java.math.BigDecimal;

/**
 * Cross-exchange arbitrage configuration.
 * Values loaded from ApolloConfig on each scan.
 */
public class ArbitrageConfig {
    private boolean enabled = true;
    private BigDecimal minProfitUsdt = new BigDecimal("0.5");
    private int maxOrderQty = 1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public BigDecimal getMinProfitUsdt() { return minProfitUsdt; }
    public void setMinProfitUsdt(BigDecimal minProfitUsdt) { this.minProfitUsdt = minProfitUsdt; }
    public int getMaxOrderQty() { return maxOrderQty; }
    public void setMaxOrderQty(int maxOrderQty) { this.maxOrderQty = maxOrderQty; }
}

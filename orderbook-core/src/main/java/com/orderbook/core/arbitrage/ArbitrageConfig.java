package com.orderbook.core.arbitrage;

import java.math.BigDecimal;

/**
 * 跨交易所套利配置。
 * 每次扫描时从 ApolloConfig 加载数值。
 */
public class ArbitrageConfig {
    private boolean enabled = true;
    private BigDecimal minProfitUsdt = new BigDecimal("0.5");
    private int maxOrderQty = 1;

    // Taker execution (hybrid mode)
    private boolean takerEnabled = false;
    private int takerMaxQty = 1;
    private BigDecimal takerMinProfitUsdt = new BigDecimal("1.0");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public BigDecimal getMinProfitUsdt() { return minProfitUsdt; }
    public void setMinProfitUsdt(BigDecimal minProfitUsdt) { this.minProfitUsdt = minProfitUsdt; }
    public int getMaxOrderQty() { return maxOrderQty; }
    public void setMaxOrderQty(int maxOrderQty) { this.maxOrderQty = maxOrderQty; }
    public boolean isTakerEnabled() { return takerEnabled; }
    public void setTakerEnabled(boolean takerEnabled) { this.takerEnabled = takerEnabled; }
    public int getTakerMaxQty() { return takerMaxQty; }
    public void setTakerMaxQty(int takerMaxQty) { this.takerMaxQty = takerMaxQty; }
    public BigDecimal getTakerMinProfitUsdt() { return takerMinProfitUsdt; }
    public void setTakerMinProfitUsdt(BigDecimal takerMinProfitUsdt) { this.takerMinProfitUsdt = takerMinProfitUsdt; }
}

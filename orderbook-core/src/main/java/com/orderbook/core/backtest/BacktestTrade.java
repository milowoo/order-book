package com.orderbook.core.backtest;

import java.math.BigDecimal;

public class BacktestTrade {
    private final long time;
    private final String side;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final BigDecimal fee;
    private final BigDecimal realizedPnl;

    public BacktestTrade(long time, String side, BigDecimal price, BigDecimal quantity,
                         BigDecimal fee, BigDecimal realizedPnl) {
        this.time = time;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.fee = fee;
        this.realizedPnl = realizedPnl;
    }

    public long getTime() { return time; }
    public String getSide() { return side; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getFee() { return fee; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
}

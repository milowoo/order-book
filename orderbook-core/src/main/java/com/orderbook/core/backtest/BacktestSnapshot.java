package com.orderbook.core.backtest;

import com.orderbook.core.domain.PriceLevel;

import java.math.BigDecimal;
import java.util.List;

public class BacktestSnapshot {
    private final long time;
    private final BigDecimal midPrice;
    private final BigDecimal bestBid;
    private final BigDecimal bestAsk;
    private final List<PriceLevel> bids;
    private final List<PriceLevel> asks;

    public BacktestSnapshot(long time, BigDecimal bestBid, BigDecimal bestAsk,
                            List<PriceLevel> bids, List<PriceLevel> asks) {
        this.time = time;
        this.bestBid = bestBid;
        this.bestAsk = bestAsk;
        this.midPrice = bestBid != null && bestAsk != null
                ? bestBid.add(bestAsk).divide(BigDecimal.valueOf(2), 8, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        this.bids = bids;
        this.asks = asks;
    }

    public long getTime() { return time; }
    public BigDecimal getMidPrice() { return midPrice; }
    public BigDecimal getBestBid() { return bestBid; }
    public BigDecimal getBestAsk() { return bestAsk; }
    public List<PriceLevel> getBids() { return bids; }
    public List<PriceLevel> getAsks() { return asks; }
}

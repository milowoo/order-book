package com.orderbook.core.domain;

import com.orderbook.cmd.ExchangeCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBook {

    // 卖家
    private List<PriceLevel> ask;
    // 买家
    private List<PriceLevel> bid;
    private long checksum;
    // 币对
    private String symbol;
    // 平台
    private ExchangeCode exchange;
    private long updateTime;

    public BigDecimal getBestAskPrice() {
        return getAskPrice(0);
    }

    public BigDecimal getAskPrice(int level) {
        if (ask.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return ask.get(level).getPrice();
    }

    public List<PriceLevel> bidsQueue() {
        return new ArrayList<>(bid);
    }

    public List<PriceLevel> asksQueue() {
        return new ArrayList<>(ask);
    }

    public BigDecimal getBestBidPrice() {
        return getBidPrice(0);
    }

    public BigDecimal getBidPrice(int level) {
        if (bid.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return bid.get(level).getPrice();
    }
}
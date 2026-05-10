package com.orderbook.core.strategy.alpha;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.PriceLevel;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OrderBookStore;
import lombok.extern.slf4j.Slf4j;

/**
 * 这段代码实现了一个基于盘口深度的策略。它的核心思想是：
 * 通过比较买单和卖单的力量对比，来判断当前市场是买方更强还是卖方更强。
 基于订单簿不平衡的 Alpha 信号。
 计算公式：不平衡度 = (买单量 - 卖单量) / (买单量 + 卖单量)
 正值 = 买盘压力更大（看涨），负值 = 卖盘压力更大（看跌）。
 读取参考交易所（Bybit）的订单簿来进行深度计算。
 */
@Slf4j
public class OrderFlowImbalanceAlpha implements AlphaSignal {

    private static final ExchangeCode REF_EXCHANGE = ExchangeCode.BYBIT;

    private final OrderBookStore orderBookStore;
    /** Number of price levels to include in volume calculation. */
    private final int depthLevels;

    public OrderFlowImbalanceAlpha(OrderBookStore orderBookStore, int depthLevels) {
        this.orderBookStore = orderBookStore;
        this.depthLevels = depthLevels;
    }

    @Override
    public double computeAlpha(String symbol, SymbolBo symbolBo) {
        try {
            OrderBook book = orderBookStore.get(REF_EXCHANGE, symbolBo.getSymbolId());
            if (book == null) return 0.0;

            double bidVol = sumVolume(book.getBid(), depthLevels);
            double askVol = sumVolume(book.getAsk(), depthLevels);
            double total = bidVol + askVol;

            if (total < 1e-8) return 0.0;

            return (bidVol - askVol) / total;
        } catch (Exception e) {
            log.warn("[{}] OrderFlowImbalanceAlpha failed: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    private double sumVolume(java.util.List<PriceLevel> levels, int maxLevels) {
        if (levels == null || levels.isEmpty()) return 0.0;
        int limit = Math.min(levels.size(), maxLevels);
        double sum = 0.0;
        for (int i = 0; i < limit; i++) {
            PriceLevel level = levels.get(i);
            if (level != null && level.getQuantity() != null) {
                sum += level.getQuantity().doubleValue();
            }
        }
        return sum;
    }

    @Override
    public String getName() {
        return "orderFlowImbalance";
    }
}

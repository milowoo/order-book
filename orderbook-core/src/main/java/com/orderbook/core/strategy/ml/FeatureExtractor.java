package com.orderbook.core.strategy.ml;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.PriceLevel;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.strategy.spread.VolatilityTracker;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;

/**
 * 机器学习模型输入的特征提取器。
 *
 * 从市场数据中提取归一化特征：
 1.
 2. log_return_1 — 1 个周期的对数收益率
 3.
 4. log_return_3 — 3 个周期的对数收益率
 5.
 6. log_return_5 — 5 个周期的对数收益率
 7.
 8. log_return_10 — 10 个周期的对数收益率
 9.
 10. volatility — 变异系数（衡量波动率）
 11.
 12. spread_bps — 买卖价差（以基点计）
 13.
 14. imbalance — 订单簿不平衡度 [-1, 1]
 15.
 16. bid_volume — 总买单量的对数（前几档）
 17.
 18. ask_volume — 总卖单量的对数（前几档）
 19.
 20. price_level — 当前价格相对于 5周期均线的位置
 21.
 22. */
@Slf4j
public class FeatureExtractor {

    private static final ExchangeCode REF_EXCHANGE = ExchangeCode.BYBIT;
    private static final int FEATURE_COUNT = 10;
    private static final int DEPTH_LEVELS = 10;

    private final OrderBookStore orderBookStore;
    private final VolatilityTracker volatilityTracker;

    public FeatureExtractor(OrderBookStore orderBookStore, VolatilityTracker volatilityTracker) {
        this.orderBookStore = orderBookStore;
        this.volatilityTracker = volatilityTracker;
    }

    /** Number of features produced. */
    public int featureCount() {
        return FEATURE_COUNT;
    }

    /** Feature names for reference. */
    public static String[] featureNames() {
        return new String[]{
                "log_return_1", "log_return_3", "log_return_5", "log_return_10",
                "volatility", "spread_bps", "imbalance", "bid_volume", "ask_volume",
                "price_level"
        };
    }

    /**
     提取某个交易对的特征。
     @return 长度为 FEATURE_COUNT 的特征数组，如果数据不足则返回 null
     */
    public double[] extract(String symbol, SymbolBo symbolBo) {
        LinkedList<BigDecimal> history = volatilityTracker.getPriceHistory(symbol);
        if (history == null || history.size() < 2) return null;

        double[] features = new double[FEATURE_COUNT];
        synchronized (history) {
            int n = history.size();
            if (n < 2) return null;

            double currentPrice = history.getLast().doubleValue();

            // --- Returns ---
            features[0] = logReturn(history, n, 1);   // 1-period return
            features[1] = n >= 4 ? logReturn(history, n, 3) : 0.0;  // 3-period
            features[2] = n >= 6 ? logReturn(history, n, 5) : 0.0;  // 5-period
            features[3] = n >= 11 ? logReturn(history, n, 10) : 0.0; // 10-period

            // --- Volatility ---
            features[4] = volatilityTracker.getVolatility(symbol).doubleValue();

            // --- Order book features ---
            OrderBook book = orderBookStore.get(REF_EXCHANGE, symbolBo.getSymbolId());
            if (book != null) {
                double bestBid = book.getBid().isEmpty() ? 0 : book.getBid().get(0).getPrice().doubleValue();
                double bestAsk = book.getAsk().isEmpty() ? 0 : book.getAsk().get(0).getPrice().doubleValue();

                // Spread in bps
                double mid = (bestBid + bestAsk) / 2.0;
                features[5] = mid > 1e-8 ? (bestAsk - bestBid) / mid * 10000 : 0.0;

                // Order book imbalance
                double bidVol = sumVolume(book.getBid(), DEPTH_LEVELS);
                double askVol = sumVolume(book.getAsk(), DEPTH_LEVELS);
                double total = bidVol + askVol;
                features[6] = total > 1e-8 ? (bidVol - askVol) / total : 0.0;

                // Log volumes (signed: positive for bid dominance)
                features[7] = Math.log(Math.max(bidVol, 1.0));
                features[8] = Math.log(Math.max(askVol, 1.0));

                // Price level relative to simple moving average (last 5)
                features[9] = priceLevel(history, currentPrice);
            }
        }

        return features;
    }

    /** Log return over lookback periods. */
    private double logReturn(LinkedList<BigDecimal> history, int size, int lookback) {
        double current = history.getLast().doubleValue();
        double past = history.get(size - 1 - lookback).doubleValue();
        if (past < 1e-12) return 0.0;
        return Math.log(current / past);
    }

    /** Price relative to MA(5). Positive = above MA. */
    private double priceLevel(LinkedList<BigDecimal> history, double currentPrice) {
        int n = Math.min(history.size(), 5);
        double sum = 0.0;
        int i = 0;
        for (BigDecimal p : history) {
            if (i >= history.size() - n) sum += p.doubleValue();
            i++;
        }
        double ma = sum / n;
        return ma > 1e-12 ? (currentPrice - ma) / ma : 0.0;
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
}

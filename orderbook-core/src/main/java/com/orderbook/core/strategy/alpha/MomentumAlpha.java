package com.orderbook.core.strategy.alpha;

import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.strategy.spread.VolatilityTracker;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;

/**
 这段代码实现了一个非常经典的动量策略。它的核心逻辑很简单：
 通过比较当前价格和过去某个时间点的价格，来判断现在的趋势是涨是跌，
 然后用数学函数把这个涨跌幅限制在 -1 到 1 之间。
 基于短期价格动量的 Alpha 信号。
 计算可配置回看周期内的变化率：
 动量 = (当前价格 - N周期前的价格) / N周期前的价格
 结果通过 Sigmoid 函数钳位（归一化）到 [-1, 1]。
 使用波动率追踪器的价格历史作为数据源。
 */
@Slf4j
public class MomentumAlpha implements AlphaSignal {

    private final VolatilityTracker volatilityTracker;
    private final int lookbackPeriods;

    /**
     * @param volatilityTracker shared price history tracker
     * @param lookbackPeriods   number of periods to look back for momentum (>= 2)
     */
    public MomentumAlpha(VolatilityTracker volatilityTracker, int lookbackPeriods) {
        this.volatilityTracker = volatilityTracker;
        this.lookbackPeriods = Math.max(lookbackPeriods, 2);
    }

    @Override
    public double computeAlpha(String symbol, SymbolBo symbolBo) {
        try {
            LinkedList<BigDecimal> history = volatilityTracker.getPriceHistory(symbol);
            if (history == null || history.size() < lookbackPeriods) return 0.0;

            BigDecimal currentPrice;
            BigDecimal pastPrice;
            synchronized (history) {
                if (history.isEmpty()) return 0.0;
                currentPrice = history.getLast();
                int pastIndex = history.size() - Math.min(lookbackPeriods, history.size());
                if (pastIndex < 0) return 0.0;
                pastPrice = history.get(pastIndex);
            }

            if (pastPrice == null || pastPrice.compareTo(BigDecimal.ZERO) == 0) return 0.0;
            if (currentPrice == null) return 0.0;

            double roc = currentPrice.subtract(pastPrice)
                    .divide(pastPrice, 12, RoundingMode.HALF_UP)
                    .doubleValue();

            return sigmoidClamp(roc, 0.1);
        } catch (Exception e) {
            log.warn("[{}] MomentumAlpha failed: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    /**
     类 Sigmoid 钳位：将输入映射到 [-1, 1]，带有灵敏度控制。
     灵敏度越高 = 对微小变化的反应越剧烈。
     */
    private static double sigmoidClamp(double value, double sensitivity) {
        double scaled = value / sensitivity;
        return Math.tanh(scaled);
    }

    @Override
    public String getName() {
        return "momentum";
    }
}

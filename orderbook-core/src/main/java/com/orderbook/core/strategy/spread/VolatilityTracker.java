package com.orderbook.core.strategy.spread;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks rolling mid-price volatility per symbol.
 * <p>
 * Maintains a ring buffer of recent mid-prices and computes the
 * coefficient of variation (stddev / mean) as a volatility metric.
 * Thread-safe: synchronized on per-symbol LinkedList.
 */
@Slf4j
@Component
public class VolatilityTracker {

    private final Map<String, LinkedList<BigDecimal>> priceHistory = new ConcurrentHashMap<>();
    private final Map<String, Integer> windowSizes = new ConcurrentHashMap<>();

    /**
     * Set the rolling window size for a symbol.
     */
    public void setWindowSize(String symbol, int windowSize) {
        windowSizes.put(symbol, Math.max(windowSize, 2));
    }

    /**
     * Record a mid-price observation.
     */
    public void recordPrice(String symbol, BigDecimal midPrice) {
        int window = windowSizes.getOrDefault(symbol, 20);
        LinkedList<BigDecimal> history = priceHistory.computeIfAbsent(symbol, k -> new LinkedList<>());
        synchronized (history) {
            history.addLast(midPrice);
            if (history.size() > window) {
                history.removeFirst();
            }
        }
    }

    /**
     * Returns the coefficient of variation (stddev / mean) as a measure of volatility.
     * Returns BigDecimal.ZERO if insufficient data (< 2 samples).
     */
    public BigDecimal getVolatility(String symbol) {
        LinkedList<BigDecimal> history = priceHistory.get(symbol);
        if (history == null || history.size() < 2) return BigDecimal.ZERO;

        synchronized (history) {
            int n = history.size();
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal p : history) sum = sum.add(p);
            BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 12, RoundingMode.HALF_UP);

            BigDecimal sumSqDiff = BigDecimal.ZERO;
            for (BigDecimal p : history) {
                BigDecimal diff = p.subtract(mean);
                sumSqDiff = sumSqDiff.add(diff.multiply(diff));
            }
            BigDecimal variance = sumSqDiff.divide(BigDecimal.valueOf(n), 12, RoundingMode.HALF_UP);
            BigDecimal stddev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));

            if (mean.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
            return stddev.divide(mean, 8, RoundingMode.HALF_UP);
        }
    }

    /** Get the current window size for a symbol. */
    public int getWindowSize(String symbol) {
        return windowSizes.getOrDefault(symbol, 20);
    }

    /** Clear state for a symbol. */
    public void clear(String symbol) {
        priceHistory.remove(symbol);
        windowSizes.remove(symbol);
    }
}

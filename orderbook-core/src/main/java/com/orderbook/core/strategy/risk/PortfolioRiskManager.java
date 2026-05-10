package com.orderbook.core.strategy.risk;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.config.ApolloConfig;
import com.orderbook.core.domain.BalanceBo;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.PnlSnapshot;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.service.PnlService;
import com.orderbook.core.store.AccountStore;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.store.SymbolStore;
import com.orderbook.core.strategy.spread.VolatilityTracker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Portfolio-level risk manager.
 * Aggregates positions across all symbols and computes portfolio-level risk metrics
 * such as total value, drawdown, VaR, Sharpe ratio, and correlation matrix.
 * Refreshed periodically on a @Scheduled timer.
 */
@Slf4j
@Service
public class PortfolioRiskManager {

    private static final ExchangeCode BALANCE_EXCHANGE = ExchangeCode.OSL_GLOBAL;
    private static final ExchangeCode PRICE_EXCHANGE = ExchangeCode.BYBIT;

    private final AccountStore accountStore;
    private final OrderBookStore orderBookStore;
    private final VolatilityTracker volatilityTracker;
    private final PnlService pnlService;
    private final SymbolStore symbolStore;
    private final ApolloConfig apolloConfig;

    // Cached portfolio metrics
    private volatile BigDecimal totalPortfolioValue = BigDecimal.ZERO;
    private volatile BigDecimal peakPortfolioValue = BigDecimal.ZERO;
    private volatile BigDecimal drawdownPct = BigDecimal.ZERO;
    private volatile boolean initialized = false;
    private volatile double portfolioVaR95 = 0.0;
    private volatile double portfolioSharpeRatio = 0.0;

    // Per-symbol concentration (symbol -> % of portfolio)
    private final Map<String, Double> symbolConcentration = new ConcurrentHashMap<>();

    // Correlation matrix (symbolA -> (symbolB -> correlation))
    private final Map<String, Map<String, Double>> correlationMatrix = new ConcurrentHashMap<>();

    // Portfolio return series for VaR/Sharpe (rolling window)
    private final LinkedList<Double> portfolioReturns = new LinkedList<>();
    private static final int MAX_RETURNS = 500;

    // Mid prices per symbol (cached for other risk checks)
    private final Map<String, BigDecimal> currentMidPrices = new ConcurrentHashMap<>();

    public PortfolioRiskManager(AccountStore accountStore,
                                OrderBookStore orderBookStore,
                                VolatilityTracker volatilityTracker,
                                PnlService pnlService,
                                SymbolStore symbolStore,
                                ApolloConfig apolloConfig) {
        this.accountStore = accountStore;
        this.orderBookStore = orderBookStore;
        this.volatilityTracker = volatilityTracker;
        this.pnlService = pnlService;
        this.symbolStore = symbolStore;
        this.apolloConfig = apolloConfig;
    }

    @PostConstruct
    public void init() {
        log.info("[PortfolioRisk] Initialized with {} active symbols", symbolStore.getActiveSymbols().size());
    }

    @Scheduled(fixedDelay = 5000)
    public void refresh() {
        try {
            computePortfolioMetrics();
        } catch (Exception e) {
            log.warn("[PortfolioRisk] Refresh failed", e);
        }
    }

    private void computePortfolioMetrics() {
        List<SymbolBo> activeSymbols = symbolStore.getActiveSymbols();
        if (activeSymbols.isEmpty()) return;

        // Step 1: Compute mid prices and positions for each symbol
        BigDecimal portfolioValue = BigDecimal.ZERO;
        Map<String, BigDecimal> symbolValues = new HashMap<>();
        Map<String, BigDecimal> logReturns = new LinkedHashMap<>();

        for (SymbolBo symbolBo : activeSymbols) {
            String symbol = symbolBo.getSymbolId();
            String symbolName = symbolBo.getSymbol();

            // Get mid price
            BigDecimal midPrice = getMidPrice(symbolBo);
            if (midPrice == null) continue;
            currentMidPrices.put(symbol, midPrice);

            // Get position from PnlService
            PnlSnapshot snap = pnlService.getSnapshot(symbol);
            BigDecimal position = snap != null && snap.getCurrentPosition() != null
                    ? snap.getCurrentPosition() : BigDecimal.ZERO;

            BigDecimal positionValue = position.multiply(midPrice);
            symbolValues.put(symbol, positionValue);
            portfolioValue = portfolioValue.add(positionValue);
        }

        // Add free USDT balance
        try {
            BalanceBo usdtBal = AccountStore.getAccount(BALANCE_EXCHANGE).getBalance("USDT");
            if (usdtBal != null) {
                portfolioValue = portfolioValue.add(usdtBal.getAvailable());
            }
        } catch (Exception e) {
            log.warn("[PortfolioRisk] Failed to get USDT balance", e);
        }

        // Step 2: Update portfolio value and peak tracking
        totalPortfolioValue = portfolioValue;
        if (!initialized || portfolioValue.compareTo(peakPortfolioValue) > 0) {
            peakPortfolioValue = portfolioValue;
            initialized = true;
        }

        // Step 3: Compute drawdown
        if (initialized && peakPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            drawdownPct = peakPortfolioValue.subtract(portfolioValue)
                    .divide(peakPortfolioValue, 6, RoundingMode.HALF_UP);
        }

        // Step 4: Compute per-symbol concentration
        for (Map.Entry<String, BigDecimal> entry : symbolValues.entrySet()) {
            if (portfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                double conc = entry.getValue().divide(portfolioValue, 6, RoundingMode.HALF_UP).doubleValue();
                symbolConcentration.put(entry.getKey(), conc);
            } else {
                symbolConcentration.put(entry.getKey(), 0.0);
            }
        }

        // Step 5: Compute portfolio returns for VaR/Sharpe
        computePortfolioReturns(activeSymbols);

        // Step 6: VaR and Sharpe from return series
        computeRiskMetrics();

        // Step 7: Correlation matrix
        computeCorrelationMatrix(activeSymbols);
    }

    private BigDecimal getMidPrice(SymbolBo symbolBo) {
        OrderBook book = orderBookStore.get(PRICE_EXCHANGE, symbolBo.getSymbolId());
        if (book == null || book.getBid().isEmpty() || book.getAsk().isEmpty()) return null;
        return book.getBestBidPrice()
                .add(book.getBestAskPrice())
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
    }

    private void computePortfolioReturns(List<SymbolBo> activeSymbols) {
        // For each symbol, get returns from VolatilityTracker price history
        // Portfolio return = weighted average of symbol returns
        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) <= 0) return;

        // Use mid price changes from VolatilityTracker snapshots
        // Portfolio return = sum(weight_i * (price_i,t - price_i,t-1) / price_i,t-1)
        for (SymbolBo symbolBo : activeSymbols) {
            String symbolName = symbolBo.getSymbol();
            LinkedList<BigDecimal> history = volatilityTracker.getPriceHistory(symbolName);
            if (history == null || history.size() < 2) continue;

            synchronized (history) {
                BigDecimal weight = BigDecimal.valueOf(symbolConcentration.getOrDefault(symbolBo.getSymbolId(), 0.0));
                if (weight.compareTo(BigDecimal.ZERO) <= 0) continue;

                // Compute price return (last price / prev price - 1)
                BigDecimal lastPrice = history.getLast();
                BigDecimal prevPrice = history.get(history.size() - 2);
                if (prevPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

                double returnVal = lastPrice.subtract(prevPrice).divide(prevPrice, 12, RoundingMode.HALF_UP).doubleValue();
                double weightedReturn = returnVal * weight.doubleValue();

                synchronized (portfolioReturns) {
                    portfolioReturns.addLast(weightedReturn);
                    while (portfolioReturns.size() > MAX_RETURNS) {
                        portfolioReturns.removeFirst();
                    }
                }
            }
            break; // Use only first symbol's returns as a simplified portfolio return
        }
    }

    private void computeRiskMetrics() {
        synchronized (portfolioReturns) {
            if (portfolioReturns.size() < 5) return;

            double[] returns = portfolioReturns.stream().mapToDouble(Double::doubleValue).toArray();
            int n = returns.length;

            // Mean return
            double sum = 0;
            for (double r : returns) sum += r;
            double mean = sum / n;

            // Std dev
            double sumSqDiff = 0;
            for (double r : returns) sumSqDiff += (r - mean) * (r - mean);
            double stddev = Math.sqrt(sumSqDiff / n);

            if (stddev > 0) {
                // VaR(95): 5th percentile (sorted returns)
                double[] sorted = returns.clone();
                Arrays.sort(sorted);
                int idx = (int) Math.ceil(n * 0.05) - 1;
                portfolioVaR95 = Math.max(0, Math.abs(sorted[Math.max(0, idx)]));

                // Sharpe: mean / std * sqrt(252) (annualized approximation)
                portfolioSharpeRatio = mean / stddev * Math.sqrt(252);
            }

            log.debug("[PortfolioRisk] VaR95={}, Sharpe={}, returns={}", portfolioVaR95, portfolioSharpeRatio, n);
        }
    }

    private void computeCorrelationMatrix(List<SymbolBo> activeSymbols) {
        // Simplified: compute correlation between each pair using price histories
        correlationMatrix.clear();

        for (int i = 0; i < activeSymbols.size(); i++) {
            SymbolBo symA = activeSymbols.get(i);
            String symAId = symA.getSymbolId();
            Map<String, Double> row = new ConcurrentHashMap<>();
            correlationMatrix.put(symAId, row);

            for (int j = i + 1; j < activeSymbols.size(); j++) {
                SymbolBo symB = activeSymbols.get(j);
                String symBId = symB.getSymbolId();
                double corr = computePearsonCorrelation(symA.getSymbol(), symB.getSymbol());
                row.put(symBId, corr);

                Map<String, Double> otherRow = correlationMatrix.computeIfAbsent(symBId, k -> new ConcurrentHashMap<>());
                otherRow.put(symAId, corr);
            }
            // Self-correlation
            row.put(symAId, 1.0);
        }
    }

    private double computePearsonCorrelation(String symNameA, String symNameB) {
        LinkedList<BigDecimal> histA = volatilityTracker.getPriceHistory(symNameA);
        LinkedList<BigDecimal> histB = volatilityTracker.getPriceHistory(symNameB);
        if (histA == null || histB == null || histA.size() < 3 || histB.size() < 3) return 0.0;

        // Compute returns for both symbols
        List<Double> returnsA;
        List<Double> returnsB;
        synchronized (histA) { returnsA = computeReturns(histA); }
        synchronized (histB) { returnsB = computeReturns(histB); }

        // Use min length
        int n = Math.min(returnsA.size(), returnsB.size());
        if (n < 3) return 0.0;

        double[] a = new double[n];
        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = returnsA.get(returnsA.size() - n + i);
            b[i] = returnsB.get(returnsB.size() - n + i);
        }

        return pearson(a, b);
    }

    private List<Double> computeReturns(List<BigDecimal> prices) {
        List<Double> result = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal prev = prices.get(i - 1);
            if (prev.compareTo(BigDecimal.ZERO) > 0) {
                double r = prices.get(i).subtract(prev).divide(prev, 12, RoundingMode.HALF_UP).doubleValue();
                result.add(r);
            }
        }
        return result;
    }

    private double pearson(double[] x, double[] y) {
        int n = x.length;
        double sumX = 0, sumY = 0;
        for (int i = 0; i < n; i++) { sumX += x[i]; sumY += y[i]; }
        double meanX = sumX / n, meanY = sumY / n;

        double cov = 0, varX = 0, varY = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX, dy = y[i] - meanY;
            cov += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }

        double denom = Math.sqrt(varX * varY);
        return denom > 0 ? Math.max(-1, Math.min(1, cov / denom)) : 0.0;
    }

    // ---- Public accessors for controllers and risk checks ----

    public BigDecimal getTotalPortfolioValue() { return totalPortfolioValue; }
    public BigDecimal getPeakPortfolioValue() { return peakPortfolioValue; }
    public BigDecimal getDrawdownPct() { return drawdownPct; }
    public double getPortfolioVaR95() { return portfolioVaR95; }
    public double getPortfolioSharpeRatio() { return portfolioSharpeRatio; }

    public double getSymbolConcentration(String symbol) {
        return symbolConcentration.getOrDefault(symbol, 0.0);
    }

    public Map<String, Double> getAllConcentrations() {
        return Collections.unmodifiableMap(symbolConcentration);
    }

    public double getCorrelation(String symA, String symB) {
        Map<String, Double> row = correlationMatrix.get(symA);
        if (row == null) return 0.0;
        return row.getOrDefault(symB, 0.0);
    }

    public Map<String, Map<String, Double>> getCorrelationMatrix() {
        return Collections.unmodifiableMap(correlationMatrix);
    }

    public BigDecimal getMidPrice(String symbol) {
        return currentMidPrices.getOrDefault(symbol, BigDecimal.ZERO);
    }
}

package com.orderbook.core.monitor;

import com.orderbook.core.arbitrage.ArbitrageService;
import com.orderbook.core.domain.PnlSnapshot;
import com.orderbook.core.exchange.handler.*;
import com.orderbook.core.service.PnlService;
import com.orderbook.core.store.OpenOrdersStore;
import com.orderbook.core.strategy.risk.PortfolioRiskManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registers and refreshes Micrometer metrics for Prometheus/Grafana monitoring.
 * Gauges are backed by AtomicReference values updated on a @Scheduled timer.
 */
@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry registry;
    private final PnlService pnlService;
    private final BybitHandler bybitHandler;
    private final BinanceHandler binanceHandler;
    private final BitgetHandler bitgetHandler;
    private final GlobalHandler globalHandler;
    private final OpenOrdersStore openOrdersStore;
    private final ArbitrageService arbitrageService;
    private final PortfolioRiskManager portfolioRiskManager;

    // Backing values for dynamically updated gauges
    private final Map<String, AtomicReference<Double>> pnlRealized = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> pnlUnrealized = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> positions = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> fillCounts = new ConcurrentHashMap<>();
    private final AtomicReference<Double> bybitHealth = new AtomicReference<>(0.0);
    private final AtomicReference<Double> binanceHealth = new AtomicReference<>(0.0);
    private final AtomicReference<Double> bitgetHealth = new AtomicReference<>(0.0);
    private final AtomicReference<Double> globalHealth = new AtomicReference<>(0.0);
    private final AtomicLong arbitrageOpportunitiesTotal = new AtomicLong(0);

    // Portfolio risk gauge backing values
    private final AtomicReference<Double> portfolioValue = new AtomicReference<>(0.0);
    private final AtomicReference<Double> portfolioDrawdown = new AtomicReference<>(0.0);
    private final AtomicReference<Double> portfolioVaR = new AtomicReference<>(0.0);
    private final AtomicReference<Double> portfolioSharpe = new AtomicReference<>(0.0);
    private final Map<String, AtomicReference<Double>> portfolioConcentration = new ConcurrentHashMap<>();

    // SOR routing metrics
    private final Map<String, AtomicLong> sorRoutingCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> sorExchangeScores = new ConcurrentHashMap<>();

    // Strategy latency timer
    private final Timer strategyLatency;

    public MetricsService(MeterRegistry registry, PnlService pnlService,
                          BybitHandler bybitHandler, BinanceHandler binanceHandler,
                          BitgetHandler bitgetHandler, GlobalHandler globalHandler,
                          OpenOrdersStore openOrdersStore, ArbitrageService arbitrageService,
                          PortfolioRiskManager portfolioRiskManager) {
        this.registry = registry;
        this.pnlService = pnlService;
        this.bybitHandler = bybitHandler;
        this.binanceHandler = binanceHandler;
        this.bitgetHandler = bitgetHandler;
        this.globalHandler = globalHandler;
        this.openOrdersStore = openOrdersStore;
        this.arbitrageService = arbitrageService;
        this.portfolioRiskManager = portfolioRiskManager;

        this.strategyLatency = Timer.builder("orderbook_strategy_latency")
                .description("Strategy execution latency")
                .publishPercentiles(new double[]{0.5, 0.95, 0.99})
                .register(registry);
    }

    @PostConstruct
    public void init() {
        log.info("[Metrics] Initializing Prometheus metrics...");

        // PnL gauges — value refreshed via callback on each scrape
        Gauge.builder("orderbook_pnl_realized", pnlRealized,
                        map -> map.values().stream().mapToDouble(AtomicReference::get).sum())
                .description("Total realized PnL across all symbols")
                .register(registry);

        Gauge.builder("orderbook_pnl_unrealized", pnlUnrealized,
                        map -> map.values().stream().mapToDouble(AtomicReference::get).sum())
                .description("Total unrealized PnL across all symbols")
                .register(registry);

        Gauge.builder("orderbook_position_net", positions,
                        map -> map.values().stream().mapToDouble(AtomicReference::get).sum())
                .description("Net positions across all symbols")
                .register(registry);

        // Connection health gauges
        Gauge.builder("orderbook_connection_healthy", bybitHealth, AtomicReference::get)
                .tag("exchange", "BYBIT").description("Bybit connection health (1=UP, 0=DOWN)")
                .register(registry);
        Gauge.builder("orderbook_connection_healthy", binanceHealth, AtomicReference::get)
                .tag("exchange", "BINANCE").description("Binance connection health (1=UP, 0=DOWN)")
                .register(registry);
        Gauge.builder("orderbook_connection_healthy", bitgetHealth, AtomicReference::get)
                .tag("exchange", "BITGET").description("Bitget connection health (1=UP, 0=DOWN)")
                .register(registry);
        Gauge.builder("orderbook_connection_healthy", globalHealth, AtomicReference::get)
                .tag("exchange", "OSL_GLOBAL").description("Global connection health (1=UP, 0=DOWN)")
                .register(registry);

        // Arbitrage metrics
        Gauge.builder("orderbook_arbitrage_opportunities_total", arbitrageOpportunitiesTotal, AtomicLong::get)
                .description("Total arbitrage opportunities detected")
                .register(registry);

        // Portfolio risk gauges
        Gauge.builder("orderbook_portfolio_value", portfolioValue, AtomicReference::get)
                .description("Total portfolio value (positions + free balance)")
                .register(registry);
        Gauge.builder("orderbook_portfolio_drawdown_pct", portfolioDrawdown, AtomicReference::get)
                .description("Portfolio drawdown from peak")
                .register(registry);
        Gauge.builder("orderbook_portfolio_var_95", portfolioVaR, AtomicReference::get)
                .description("Portfolio VaR at 95% confidence")
                .register(registry);
        Gauge.builder("orderbook_portfolio_sharpe_ratio", portfolioSharpe, AtomicReference::get)
                .description("Portfolio annualized Sharpe ratio")
                .register(registry);

        // SOR routing count gauge
        Gauge.builder("orderbook_sor_routing_total", sorRoutingCounts,
                        map -> map.values().stream().mapToLong(AtomicLong::get).sum())
                .description("Total SOR routing decisions per exchange")
                .register(registry);

        log.info("[Metrics] Prometheus metrics initialized");
    }

    private void refreshMLMetrics() {
        // ML metrics are updated on-demand via TrainingController
    }

    private void refreshSORMetrics() {
        // SOR metrics updated on-demand via recordSORRouting()
    }

    /**
     * Refresh all metric values — called periodically or from strategy tick.
     */
    @Scheduled(fixedDelay = 5000)
    public void refreshAll() {
        refreshPnlMetrics();
        refreshConnectionHealth();
        refreshArbitrageMetrics();
        refreshPortfolioMetrics();
    }

    private void refreshPnlMetrics() {
        var snapshots = pnlService.getAllSnapshots();
        for (Map.Entry<String, PnlSnapshot> entry : snapshots.entrySet()) {
            String symbol = entry.getKey();
            PnlSnapshot snap = entry.getValue();

            pnlRealized.computeIfAbsent(symbol, k -> new AtomicReference<>(0.0))
                    .set(snap.getRealizedPnl() != null ? snap.getRealizedPnl().doubleValue() : 0.0);
            pnlUnrealized.computeIfAbsent(symbol, k -> new AtomicReference<>(0.0))
                    .set(snap.getUnrealizedPnl() != null ? snap.getUnrealizedPnl().doubleValue() : 0.0);
            positions.computeIfAbsent(symbol, k -> new AtomicReference<>(0.0))
                    .set(snap.getCurrentPosition() != null ? snap.getCurrentPosition().doubleValue() : 0.0);
            fillCounts.computeIfAbsent(symbol, k -> new AtomicReference<>(0.0))
                    .set((double) snap.getTradeCount());
        }
    }

    private void refreshConnectionHealth() {
        bybitHealth.set(bybitHandler.isConnectionHealthy() ? 1.0 : 0.0);
        binanceHealth.set(binanceHandler.isConnectionHealthy() ? 1.0 : 0.0);
        bitgetHealth.set(bitgetHandler.isConnectionHealthy() ? 1.0 : 0.0);
        globalHealth.set(globalHandler.isConnectionHealthy() ? 1.0 : 0.0);
    }

    private void refreshArbitrageMetrics() {
        try {
            Map<String, Object> stats = arbitrageService.getStats();
            Object total = stats.get("totalOpportunities");
            arbitrageOpportunitiesTotal.set(total instanceof Number ? ((Number) total).longValue() : 0);
        } catch (Exception e) {
            log.warn("[Metrics] Failed to refresh arbitrage metrics", e);
        }
    }

    private void refreshPortfolioMetrics() {
        try {
            portfolioValue.set(portfolioRiskManager.getTotalPortfolioValue().doubleValue());
            portfolioDrawdown.set(portfolioRiskManager.getDrawdownPct().doubleValue());
            portfolioVaR.set(portfolioRiskManager.getPortfolioVaR95());
            portfolioSharpe.set(portfolioRiskManager.getPortfolioSharpeRatio());

            // Per-symbol concentration
            Map<String, Double> concentrations = portfolioRiskManager.getAllConcentrations();
            for (Map.Entry<String, Double> entry : concentrations.entrySet()) {
                portfolioConcentration.computeIfAbsent(entry.getKey(), k -> new AtomicReference<>(0.0))
                        .set(entry.getValue());
            }

            // Ensure concentration gauges are registered for each symbol
            for (Map.Entry<String, Double> entry : concentrations.entrySet()) {
                String symbol = entry.getKey();
                String gaugeName = "orderbook_portfolio_concentration";
                // Idempotent registration — only registers once per symbol
                registry.find(gaugeName).tag("symbol", symbol).gauge();
                if (registry.find(gaugeName).tag("symbol", symbol).gauge() == null) {
                    Gauge.builder(gaugeName, portfolioConcentration.get(symbol), AtomicReference::get)
                            .tag("symbol", symbol)
                            .description("Per-symbol portfolio concentration")
                            .register(registry);
                }
            }
        } catch (Exception e) {
            log.warn("[Metrics] Failed to refresh portfolio metrics", e);
        }
    }

    /**
     * Record an SOR routing decision for metrics.
     */
    public void recordSORRouting(String exchange, double score) {
        sorRoutingCounts.computeIfAbsent(exchange, k -> new AtomicLong(0)).incrementAndGet();
        sorExchangeScores.computeIfAbsent(exchange, k -> new AtomicReference<>(0.0)).set(score);
    }

    /**
     * Record strategy execution time for latency metrics.
     */
    public Timer.Sample startLatencyTimer() {
        return Timer.start(registry);
    }

    /**
     * Stop latency timer and record the duration.
     */
    public void stopLatencyTimer(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(strategyLatency);
        }
    }
}

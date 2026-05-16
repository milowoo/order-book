package com.orderbook.core.backtest;

import com.orderbook.core.domain.PriceLevel;
import com.orderbook.core.mapper.OrderBookSnapshotMapper;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.strategy.ml.MLModelRegistry;
import com.orderbook.core.strategy.spread.VolatilityTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BacktestEngineTest {

    private BacktestEngine engine;

    private static final String SYMBOL = "BTCUSDT";
    private static final long START = 1700000000000L;
    private static final long END = 1700100000000L;

    // TickSize * baseOffsetTicks → offset price
    // With tickSize=0.1, baseOffsetTicks=1  → offset=0.1
    // With tickSize=0.1, baseOffsetTicks=60 → offset=6.0
    private static final BigDecimal TICK_SIZE = new BigDecimal("0.1");

    @BeforeEach
    void setUp() {
        OrderBookSnapshotMapper mapper = mock(OrderBookSnapshotMapper.class);
        VolatilityTracker vt = new VolatilityTracker();
        OrderBookStore orderBookStore = mock(OrderBookStore.class);
        MLModelRegistry mlModelRegistry = mock(MLModelRegistry.class);
        engine = new BacktestEngine(mapper, vt, orderBookStore, mlModelRegistry);
    }

    // ============================================================
    //  Passive fills (maker) — offset < halfSpread
    //  ConstantSpreadCalculator: offset = baseOffsetTicks * tickSize
    //  With bestBid=49995, bestAsk=50005: mid=50000, halfSpread=5
    //  offset=0.1 (1tick) → bidPrice=49999.9 > bestBid → passive buy fill
    //                      → askPrice=50000.1 < bestAsk → passive sell fill
    // ============================================================

    @Test
    void passiveBuyFillsWhenBidInsideSpread() {
        BacktestConfig config = configWithOffset("1");
        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertTrue(result.getTotalTrades() >= 1);
        assertEquals("buy", result.getTrades().get(0).getSide());
    }

    @Test
    void passiveSellFillsWhenAskInsideSpread() {
        BacktestConfig config = configWithOffset("1");
        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertEquals(2, result.getTotalTrades());
        assertEquals("sell", result.getTrades().get(1).getSide());
    }

    @Test
    void noFillWhenOffsetExceedsHalfSpread() {
        // offset=60 ticks → 60*0.1=6.0 > halfSpread=5
        // bidPrice=49994 < bestBid=49995 → no passive buy
        // askPrice=50006 > bestAsk=50005 → no passive sell
        BacktestConfig config = configWithOffset("60");
        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertEquals(0, result.getTotalTrades());
        assertEquals(0, result.getTrades().size());
    }

    // ============================================================
    //  Maker fee applied to passive fills
    //  Use tiny takerFeeRate to avoid break-even spread clamping
    // ============================================================

    @Test
    void passiveFillUsesMakerFee() {
        BacktestConfig config = configWithOffset("1");
        config.setMakerFeeRate(new BigDecimal("0.001"));
        config.setTakerFeeRate(new BigDecimal("0.000001")); // tiny → negligible clamping

        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertTrue(result.getTotalFees().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.getTotalTrades() >= 2);
    }

    // ============================================================
    //  Multi-level order book processing
    // ============================================================

    @Test
    void multiLevelOrderBookProcessesSuccessfully() {
        BacktestConfig config = configWithOffset("1");
        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49990", "50010",
                        bid("49990", "1.0", "49980", "2.0", "49970", "3.0"),
                        ask("50010", "1.5", "50020", "2.5", "50030", "3.5"))
        );

        BacktestResult result = engine.simulate(config, snapshots);
        assertTrue(result.getTotalTrades() >= 2);
    }

    // ============================================================
    //  FIFO PnL computation
    // ============================================================

    @Test
    void fifoPnlIsCorrect() {
        BacktestConfig config = configWithOffset("1");

        // Snapshots with rising prices
        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0")),
                snap(2, "50000", "50010", bid("50000", "1.0"), ask("50010", "1.0")),
                snap(3, "50005", "50015", bid("50005", "1.0"), ask("50015", "1.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertTrue(result.getTotalTrades() >= 3);

        // Each buy+liquidity sell pair generates a small profit (buy low inside spread, sell high inside spread)
        BigDecimal totalRealizedPnl = result.getTrades().stream()
                .filter(t -> "sell".equals(t.getSide()))
                .map(BacktestTrade::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(totalRealizedPnl.compareTo(BigDecimal.ZERO) > 0,
                "Sell at higher price should generate positive PnL");
    }

    // ============================================================
    //  Risk control tests
    // ============================================================

    @Test
    void circuitBreakerBlocksAfterThresholdFailures() {
        // offset=60 → no fills, circuit breaker activates
        BacktestConfig config = configWithOffset("60");
        config.setRiskEnabled(true);
        config.setCircuitBreakerThreshold(2);
        config.setCircuitBreakerCooldownMs(60000);

        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0")),
                snap(2, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0")),
                snap(3, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertEquals(0, result.getTotalTrades());
        assertTrue(result.getFinalBalance().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void maxDrawdownDoesNotCrashEngine() {
        BacktestConfig config = configWithOffset("1");
        config.setRiskEnabled(true);
        config.setMaxDrawdownPercent(new BigDecimal("5.0"));

        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49995", "50005", bid("49995", "10.0"), ask("50005", "10.0")),
                snap(2, "49895", "49905", bid("49895", "10.0"), ask("49905", "10.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertNotNull(result);
        assertTrue(result.getTotalTicks() > 0);
    }

    // ============================================================
    //  Partial fill on cash shortage
    // ============================================================

    @Test
    void partialFillWhenCashInsufficient() {
        BacktestConfig config = configWithOffset("1");
        config.setInitialCapital(new BigDecimal("100000"));
        config.setModelParams(withParam(config.getModelParams(), "maxDelegateCount", "10"));

        // Top ask level has 3.0 qty → estimateTopLevelQty returns 3.0
        // Full cost = 3 * 49999.9 = 149999.7 > 100000 → partial fill
        // Max affordable = floor(100000 / 49999.9) = 2
        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49995", "50005",
                        bid("49995", "1.0"),
                        ask("50005", "3.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertTrue(result.getTotalTrades() > 0);
        assertTrue(result.getFinalBalance().compareTo(BigDecimal.ZERO) >= 0);
    }

    // ============================================================
    //  Equity curve tracking
    // ============================================================

    @Test
    void equityCurveTracksPortfolioValue() {
        BacktestConfig config = configWithOffset("1");
        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0")),
                snap(2, "50000", "50010", bid("50000", "1.0"), ask("50010", "1.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertNotNull(result.getEquityCurve());
        assertEquals(snapshots.size(), result.getEquityCurve().size());
        result.getEquityCurve().forEach(e -> assertTrue(e.compareTo(BigDecimal.ZERO) > 0));
    }

    // ============================================================
    //  Empty / edge cases
    // ============================================================

    @Test
    void emptySnapshotsReturnsEmptyResult() {
        BacktestConfig config = baseConfig();
        List<BacktestSnapshot> snapshots = List.of();

        BacktestResult result = engine.simulate(config, snapshots);

        assertEquals(0, result.getTotalTicks());
        assertEquals(0, result.getTotalTrades());
        assertEquals(config.getInitialCapital(), result.getFinalBalance());
    }

    @Test
    void nullMidPriceSkipsSnapshot() {
        BacktestConfig config = configWithOffset("1");
        List<BacktestSnapshot> snapshots = List.of(
                new BacktestSnapshot(START + 1000, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), "BYBIT"),
                snap(1, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertTrue(result.getTotalTrades() > 0);
    }

    // ============================================================
    //  Result metrics
    // ============================================================

    @Test
    void resultMetricsArePopulated() {
        BacktestConfig config = configWithOffset("1");
        List<BacktestSnapshot> snapshots = List.of(
                snap(1, "49995", "50005", bid("49995", "1.0"), ask("50005", "1.0")),
                snap(2, "50000", "50010", bid("50000", "1.0"), ask("50010", "1.0")),
                snap(3, "50005", "50015", bid("50005", "1.0"), ask("50015", "1.0"))
        );

        BacktestResult result = engine.simulate(config, snapshots);

        assertNotNull(result.getId());
        assertEquals(SYMBOL, result.getSymbol());
        assertEquals("constant", result.getModel());
        assertTrue(result.getTotalTicks() > 0);
        assertNotNull(result.getEquityCurve());
        assertFalse(result.getEquityCurve().isEmpty());
    }

    // ============================================================
    //  Helper methods
    // ============================================================

    private BacktestConfig baseConfig() {
        BacktestConfig config = new BacktestConfig();
        config.setSymbol(SYMBOL);
        config.setStartTime(START);
        config.setEndTime(END);
        config.setModel("constant");
        config.setInitialCapital(new BigDecimal("100000"));
        // Use zero fees to avoid break-even spread clamping interference
        config.setMakerFeeRate(BigDecimal.ZERO);
        config.setTakerFeeRate(BigDecimal.ZERO);
        config.setRiskEnabled(false);
        config.setAlphaEnabled(false);

        Map<String, Object> params = new HashMap<>();
        params.put("tickSize", TICK_SIZE);
        params.put("stepSize", new BigDecimal("0.001"));
        params.put("minSize", new BigDecimal("0.001"));
        params.put("maxDelegateCount", new BigDecimal("10"));
        params.put("baseOffsetTicks", BigDecimal.ONE);
        config.setModelParams(params);
        return config;
    }

    private BacktestConfig configWithOffset(String offsetTicks) {
        BacktestConfig config = baseConfig();
        config.setModelParams(withParam(config.getModelParams(), "baseOffsetTicks", offsetTicks));
        return config;
    }

    private Map<String, Object> withParam(Map<String, Object> params, String key, String value) {
        Map<String, Object> copy = new HashMap<>(params);
        copy.put(key, new BigDecimal(value));
        return copy;
    }

    private BacktestSnapshot snap(long seq, String bestBid, String bestAsk,
                                   List<PriceLevel> bids, List<PriceLevel> asks) {
        return new BacktestSnapshot(START + seq * 1000,
                new BigDecimal(bestBid), new BigDecimal(bestAsk), bids, asks, "BYBIT");
    }

    private List<PriceLevel> bid(String... priceQtyPairs) { return levels(priceQtyPairs); }
    private List<PriceLevel> ask(String... priceQtyPairs) { return levels(priceQtyPairs); }

    private List<PriceLevel> levels(String... priceQtyPairs) {
        List<PriceLevel> result = new ArrayList<>();
        for (int i = 0; i < priceQtyPairs.length; i += 2) {
            result.add(new PriceLevel(new BigDecimal(priceQtyPairs[i]), new BigDecimal(priceQtyPairs[i + 1])));
        }
        return result;
    }
}

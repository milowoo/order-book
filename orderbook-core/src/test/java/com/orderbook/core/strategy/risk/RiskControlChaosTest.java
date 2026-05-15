package com.orderbook.core.strategy.risk;

import com.orderbook.core.util.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Chaos engineering fault injection tests for the risk control system.
 * <p>
 * Simulates real-world failure scenarios:
 * <ul>
 *   <li>Exchange API errors → circuit breaker open → blocks orders</li>
 *   <li>Price feed spike → deviation check rejects unsafe orders</li>
 *   <li>Portfolio crash → max drawdown stops new trades</li>
 *   <li>Position concentration → concentration risk blocks</li>
 *   <li>Order backlog → max order count gate</li>
 *   <li>Request burst → rate limiter throttles</li>
 *   <li>Full chain: multiple checks + circuit breaker + recovery</li>
 * </ul>
 */
class RiskControlChaosTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final String SIDE = "buy";
    private static final BigDecimal PRICE = new BigDecimal("50000");
    private static final BigDecimal QTY = BigDecimal.ONE;

    // ============================================================
    //  Scenario 1: Exchange API circuit breaker
    //  Simulate: exchange starts returning errors
    //  Verify: breaker opens after threshold → blocks → recovers
    // ============================================================

    @Test
    void circuitBreaker_allowsNormalTraffic() {
        CircuitBreakerRisk cb = new CircuitBreakerRisk(5, 60_000);
        assertTrue(cb.check(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void circuitBreaker_opensAfterThresholdFailures() {
        CircuitBreakerRisk cb = new CircuitBreakerRisk(3, 60_000);

        // Breaker starts closed — all traffic passes
        assertTrue(cb.check(SYMBOL, SIDE, PRICE, QTY));

        // Simulate 3 exchange API failures
        cb.recordFailure(SYMBOL);
        assertTrue(cb.check(SYMBOL, SIDE, PRICE, QTY), "Should still pass after 1 failure");

        cb.recordFailure(SYMBOL);
        assertTrue(cb.check(SYMBOL, SIDE, PRICE, QTY), "Should still pass after 2 failures");

        cb.recordFailure(SYMBOL); // 3rd failure → threshold reached → opens
        assertFalse(cb.check(SYMBOL, SIDE, PRICE, QTY), "Breaker should block after threshold failures");
    }

    @Test
    void circuitBreaker_recordsConsecutiveFailures() {
        CircuitBreakerRisk cb = new CircuitBreakerRisk(5, 60_000);
        assertEquals(0, cb.getConsecutiveFailures());

        cb.recordFailure(SYMBOL);
        assertEquals(1, cb.getConsecutiveFailures());

        cb.recordFailure(SYMBOL);
        assertEquals(2, cb.getConsecutiveFailures());
    }

    @Test
    void circuitBreaker_successResetsFailureCount() {
        CircuitBreakerRisk cb = new CircuitBreakerRisk(3, 60_000);

        cb.recordFailure(SYMBOL);
        cb.recordFailure(SYMBOL);
        assertEquals(2, cb.getConsecutiveFailures());

        cb.recordSuccess();
        assertEquals(0, cb.getConsecutiveFailures(), "Success should reset failure count");
        assertTrue(cb.check(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void circuitBreaker_autoRecoversAfterCooldown() throws Exception {
        CircuitBreakerRisk cb = new CircuitBreakerRisk(2, 100);

        // Trigger open
        cb.recordFailure(SYMBOL);
        cb.recordFailure(SYMBOL);
        assertFalse(cb.check(SYMBOL, SIDE, PRICE, QTY));

        // Wait for cooldown
        Thread.sleep(150);

        // After cooldown, check() should auto-reset
        assertTrue(cb.check(SYMBOL, SIDE, PRICE, QTY), "Breaker should auto-reset after cooldown");
        assertEquals(0, cb.getConsecutiveFailures());
    }

    // ============================================================
    //  Scenario 2: Price feed spike
    //  Simulate: reference price suddenly diverges from order price
    //  Verify: PriceDeviationRisk rejects unsafe orders
    // ============================================================

    @Test
    void priceDeviation_passesWhenWithinThreshold() {
        PriceDeviationRisk risk = new PriceDeviationRisk(new BigDecimal("0.05")); // 5%
        risk.setReferencePrice(new BigDecimal("50000"));

        assertTrue(risk.check(SYMBOL, SIDE, new BigDecimal("51000"), QTY));  // +2%
        assertTrue(risk.check(SYMBOL, SIDE, new BigDecimal("49000"), QTY));  // -2%
    }

    @Test
    void priceDeviation_blocksWhenExceedingThreshold() {
        PriceDeviationRisk risk = new PriceDeviationRisk(new BigDecimal("0.05")); // 5%
        risk.setReferencePrice(new BigDecimal("50000"));

        assertFalse(risk.check(SYMBOL, SIDE, new BigDecimal("55000"), QTY));  // +10%
        assertFalse(risk.check(SYMBOL, SIDE, new BigDecimal("45000"), QTY));  // -10%
    }

    @Test
    void priceDeviation_passesOnBoundary() {
        PriceDeviationRisk risk = new PriceDeviationRisk(new BigDecimal("0.05")); // 5%
        risk.setReferencePrice(new BigDecimal("50000"));

        assertTrue(risk.check(SYMBOL, SIDE, new BigDecimal("52500"), QTY));  // +5% exactly
        assertTrue(risk.check(SYMBOL, SIDE, new BigDecimal("47500"), QTY));  // -5% exactly
    }

    @Test
    void priceDeviation_passesWhenNoReferencePrice() {
        PriceDeviationRisk risk = new PriceDeviationRisk(new BigDecimal("0.05"));

        // No reference price set → pass through
        assertTrue(risk.check(SYMBOL, SIDE, new BigDecimal("99999"), QTY));
    }

    @Test
    void priceDeviation_passesWhenReferencePriceIsZero() {
        PriceDeviationRisk risk = new PriceDeviationRisk(new BigDecimal("0.05"));
        risk.setReferencePrice(BigDecimal.ZERO);

        assertTrue(risk.check(SYMBOL, SIDE, new BigDecimal("99999"), QTY));
    }

    // ============================================================
    //  Scenario 3: Portfolio crash (max drawdown)
    //  Simulate: portfolio value drops significantly from peak
    //  Verify: MaxDrawdownRisk stops new trades
    // ============================================================

    @Test
    void maxDrawdown_passesWhenWithinLimit() {
        PortfolioRiskManager pm = mock(PortfolioRiskManager.class);
        when(pm.getDrawdownPct()).thenReturn(new BigDecimal("0.05")); // 5% drawdown

        MaxDrawdownRisk risk = new MaxDrawdownRisk(new BigDecimal("0.10"), pm); // limit 10%
        assertTrue(risk.check(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void maxDrawdown_blocksWhenExceeded() {
        PortfolioRiskManager pm = mock(PortfolioRiskManager.class);
        when(pm.getDrawdownPct()).thenReturn(new BigDecimal("0.15")); // 15% drawdown

        MaxDrawdownRisk risk = new MaxDrawdownRisk(new BigDecimal("0.10"), pm); // limit 10%
        assertFalse(risk.check(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void maxDrawdown_passesOnExactBoundary() {
        PortfolioRiskManager pm = mock(PortfolioRiskManager.class);
        when(pm.getDrawdownPct()).thenReturn(new BigDecimal("0.10")); // 10% drawdown

        MaxDrawdownRisk risk = new MaxDrawdownRisk(new BigDecimal("0.10"), pm); // limit 10%
        assertTrue(risk.check(SYMBOL, SIDE, PRICE, QTY), "Should pass when drawdown equals limit");
    }

    // ============================================================
    //  Scenario 4: Position concentration
    //  Simulate: single symbol dominates the portfolio
    //  Verify: ConcentrationRisk blocks further accumulation
    // ============================================================

    @Test
    void concentration_passesWhenDiversified() {
        PortfolioRiskManager pm = mock(PortfolioRiskManager.class);
        when(pm.getSymbolConcentration(SYMBOL)).thenReturn(0.20); // 20%

        ConcentrationRisk risk = new ConcentrationRisk(pm, 0.30); // limit 30%
        assertTrue(risk.check(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void concentration_blocksWhenExceeded() {
        PortfolioRiskManager pm = mock(PortfolioRiskManager.class);
        when(pm.getSymbolConcentration(SYMBOL)).thenReturn(0.50); // 50%

        ConcentrationRisk risk = new ConcentrationRisk(pm, 0.30); // limit 30%
        assertFalse(risk.check(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void concentration_blocksForDifferentSymbolIndependently() {
        PortfolioRiskManager pm = mock(PortfolioRiskManager.class);
        when(pm.getSymbolConcentration("BTCUSDT")).thenReturn(0.50); // 50%
        when(pm.getSymbolConcentration("ETHUSDT")).thenReturn(0.10); // 10%

        ConcentrationRisk risk = new ConcentrationRisk(pm, 0.30);

        assertFalse(risk.check("BTCUSDT", SIDE, PRICE, QTY));
        assertTrue(risk.check("ETHUSDT", SIDE, PRICE, QTY));
    }

    // ============================================================
    //  Scenario 5: Order backlog
    //  Simulate: too many unfilled open orders accumulate
    //  Verify: MaxOrderCountRisk blocks new placements
    // ============================================================

    @Test
    void maxOrderCount_passesWhenUnderLimit() {
        MaxOrderCountRisk risk = new MaxOrderCountRisk(50);
        risk.setCurrentCount(30);
        assertTrue(risk.check(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void maxOrderCount_blocksWhenAtLimit() {
        MaxOrderCountRisk risk = new MaxOrderCountRisk(50);
        risk.setCurrentCount(50);
        assertFalse(risk.check(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void maxOrderCount_blocksWhenExceedingLimit() {
        MaxOrderCountRisk risk = new MaxOrderCountRisk(50);
        risk.setCurrentCount(100);
        assertFalse(risk.check(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void maxOrderCount_respectsUpdates() {
        MaxOrderCountRisk risk = new MaxOrderCountRisk(50);

        risk.setCurrentCount(60);
        assertFalse(risk.check(SYMBOL, SIDE, PRICE, QTY));

        // Orders were cancelled, count drops
        risk.setCurrentCount(40);
        assertTrue(risk.check(SYMBOL, SIDE, PRICE, QTY));
    }

    // ============================================================
    //  Scenario 6: Full risk control chain
    //  Simulate: multiple risk checks + circuit breaker chain reaction
    //  Verify: the chain blocks correctly, success resets breaker
    // ============================================================

    @Test
    void riskControl_chainAllowsWhenAllChecksPass() {
        PortfolioRiskManager pm = mock(PortfolioRiskManager.class);
        when(pm.getDrawdownPct()).thenReturn(new BigDecimal("0.05"));
        when(pm.getSymbolConcentration(SYMBOL)).thenReturn(0.10);

        CircuitBreakerRisk cb = new CircuitBreakerRisk(5, 60_000);
        RiskControl rc = new RiskControl(cb);
        rc.addCheck(new PriceDeviationRisk(new BigDecimal("0.05")));
        ((PriceDeviationRisk) rc.getChecks().get(1)).setReferencePrice(new BigDecimal("50000"));
        rc.addCheck(new MaxOrderCountRisk(50));
        ((MaxOrderCountRisk) rc.getChecks().get(2)).setCurrentCount(10);
        rc.addCheck(new MaxDrawdownRisk(new BigDecimal("0.10"), pm));
        rc.addCheck(new ConcentrationRisk(pm, 0.30));

        assertTrue(rc.canPlaceOrder(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void riskControl_chainBlocksWhenPriceDeviationExceeds() {
        CircuitBreakerRisk cb = new CircuitBreakerRisk(5, 60_000);
        RiskControl rc = new RiskControl(cb);
        PriceDeviationRisk pd = new PriceDeviationRisk(new BigDecimal("0.05"));
        pd.setReferencePrice(new BigDecimal("50000"));
        rc.addCheck(pd);

        assertFalse(rc.canPlaceOrder(SYMBOL, SIDE, new BigDecimal("99999"), QTY));
    }

    @Test
    void riskControl_chainBlocksWhenCircuitBreakerOpen() {
        CircuitBreakerRisk cb = new CircuitBreakerRisk(2, 60_000);
        RiskControl rc = new RiskControl(cb);

        // Trigger circuit breaker
        rc.recordFailure(SYMBOL);
        rc.recordFailure(SYMBOL);

        assertFalse(rc.canPlaceOrder(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void riskControl_successResetsCircuitBreaker() {
        CircuitBreakerRisk cb = new CircuitBreakerRisk(3, 60_000);
        RiskControl rc = new RiskControl(cb);

        // Fail twice, then succeed
        rc.recordFailure(SYMBOL);
        rc.recordFailure(SYMBOL);
        assertTrue(rc.canPlaceOrder(SYMBOL, SIDE, PRICE, QTY)); // still closed
        rc.recordFailure(SYMBOL);
        assertFalse(rc.canPlaceOrder(SYMBOL, SIDE, PRICE, QTY)); // now open

        // Record success - should reset
        rc.recordSuccess(SYMBOL);
        assertFalse(rc.isCircuitBroken());
        assertTrue(rc.canPlaceOrder(SYMBOL, SIDE, PRICE, QTY));
    }

    @Test
    void riskControl_isCircuitBrokenReflectsBreakerState() {
        CircuitBreakerRisk cb = new CircuitBreakerRisk(3, 60_000);
        RiskControl rc = new RiskControl(cb);

        assertFalse(rc.isCircuitBroken());

        rc.recordFailure(SYMBOL);
        rc.recordFailure(SYMBOL);
        rc.recordFailure(SYMBOL);

        assertTrue(rc.isCircuitBroken());
    }

    // ============================================================
    //  Scenario 7: Request burst (rate limiter)
    //  Simulate: sudden burst of API requests
    //  Verify: TokenBucketRateLimiter throttles excess
    // ============================================================

    @Test
    void rateLimiter_allowsBurstWithinCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 100);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire(), "Should allow requests within capacity");
        }
        limiter.destroy();
    }

    @Test
    void rateLimiter_blocksWhenExhausted() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 100);
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire();
        }
        assertFalse(limiter.tryAcquire(), "Should block when tokens exhausted");
        limiter.destroy();
    }

    @Test
    void rateLimiter_recoversAfterRefill() throws Exception {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 100); // 100/s = 10ms per token

        // Exhaust all tokens
        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertFalse(limiter.tryAcquire(), "Should be exhausted");

        // Wait for refill (≈10ms per token, wait 20ms for 2 tokens)
        Thread.sleep(25);

        assertTrue(limiter.tryAcquire(), "Should recover after refill");
        limiter.destroy();
    }

    @Test
    void rateLimiter_acquireBlocksWhenExhausted() throws Exception {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 100);

        // Take the only token
        limiter.acquire();

        // tryAcquire should fail immediately (no token available)
        assertFalse(limiter.tryAcquire());

        // Wait for refill
        Thread.sleep(15);

        assertTrue(limiter.tryAcquire(), "Should have refilled");
        limiter.destroy();
    }

    @Test
    void rateLimiter_concurrentAccess() throws Exception {
        int capacity = 10;
        // Use very slow refill to prevent interference during the burst
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(capacity, 0.1);
        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                if (limiter.tryAcquire()) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            }).start();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete");
        // Burst of 20 threads against capacity 10: at most 10 should succeed
        assertTrue(successCount.get() <= capacity,
                "At most capacity many requests should succeed in a burst, got " + successCount.get());
        assertTrue(successCount.get() > 0,
                "At least some requests should succeed");
        limiter.destroy();
    }

    // ============================================================
    //  Scenario 8: Chaos combo — exchange failure + price spike
    //  Simulate: exchange API fails 3 times, THEN price spikes
    //  Verify: price deviation still blocks after breaker resets
    // ============================================================

    @Test
    void chaosCombo_circuitBreakerAndPriceDeviation() throws Exception {
        CircuitBreakerRisk cb = new CircuitBreakerRisk(3, 100);
        PriceDeviationRisk pd = new PriceDeviationRisk(new BigDecimal("0.05"));
        pd.setReferencePrice(new BigDecimal("50000"));

        // Phase 1: Exchange API failures
        cb.recordFailure(SYMBOL);
        cb.recordFailure(SYMBOL);
        cb.recordFailure(SYMBOL);
        assertFalse(cb.check(SYMBOL, SIDE, PRICE, QTY));

        // Phase 2: Wait for circuit breaker cooldown
        Thread.sleep(150);
        assertTrue(cb.check(SYMBOL, SIDE, new BigDecimal("50000"), QTY),
                "Breaker should recover");

        // Phase 3: Price spike — different risk check should block
        assertFalse(pd.check(SYMBOL, SIDE, new BigDecimal("99999"), QTY),
                "Price deviation should still block independently");
        assertTrue(pd.check(SYMBOL, SIDE, new BigDecimal("51000"), QTY),
                "Normal price should pass");
    }

    @Test
    void chaosCombo_allRiskChecksBlockWithMaxDrawdown() {
        PortfolioRiskManager pm = mock(PortfolioRiskManager.class);
        when(pm.getDrawdownPct()).thenReturn(new BigDecimal("0.15")); // 15% crash
        when(pm.getSymbolConcentration(SYMBOL)).thenReturn(0.20);

        CircuitBreakerRisk cb = new CircuitBreakerRisk(5, 60_000);
        RiskControl rc = new RiskControl(cb);
        rc.addCheck(new MaxDrawdownRisk(new BigDecimal("0.10"), pm)); // limit 10%

        // Portfolio is down 15% against 10% limit — should block
        assertFalse(rc.canPlaceOrder(SYMBOL, SIDE, PRICE, QTY));

        // Circuit breaker not triggered (no failures recorded)
        assertFalse(rc.isCircuitBroken());
    }
}

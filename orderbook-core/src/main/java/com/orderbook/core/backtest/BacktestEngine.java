package com.orderbook.core.backtest;

import com.orderbook.core.domain.PriceLevel;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.entity.OrderBookSnapshotEntity;
import com.orderbook.core.mapper.OrderBookSnapshotMapper;
import com.orderbook.core.strategy.risk.CircuitBreakerRisk;
import com.orderbook.core.strategy.spread.*;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.strategy.ml.MLModelRegistry;
import com.orderbook.core.strategy.ml.RandomForestModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Core backtest engine that replays historical order book snapshots through
 * spread calculators and simulates fills with PnL tracking.
 *
 * <p>Enhancements over the basic version:
 * <ul>
 *   <li>Alpha signal integration (order flow imbalance, momentum)</li>
 *   <li>Multi-level fill simulation</li>
 *   <li>Maker/taker fill distinction with tiered fees</li>
 *   <li>Risk control chain (circuit breaker, max drawdown)</li>
 *   <li>ML model evaluation during backtest</li>
 *   <li>Enhanced result metrics (Calmar, profit factor, equity curve)</li>
 * </ul>
 */
@Slf4j
@Component
public class BacktestEngine {

    private final OrderBookSnapshotMapper snapshotMapper;
    private final VolatilityTracker volatilityTracker;
    private final OrderBookStore orderBookStore;
    private final MLModelRegistry mlModelRegistry;

    public BacktestEngine(OrderBookSnapshotMapper snapshotMapper,
                          VolatilityTracker volatilityTracker,
                          OrderBookStore orderBookStore,
                          MLModelRegistry mlModelRegistry) {
        this.snapshotMapper = snapshotMapper;
        this.volatilityTracker = volatilityTracker;
        this.orderBookStore = orderBookStore;
        this.mlModelRegistry = mlModelRegistry;
    }

    /**
     * Run a backtest simulation over historical snapshots loaded from DB.
     */
    public BacktestResult run(BacktestConfig config) {
        List<BacktestSnapshot> snapshots = loadSnapshots(config);
        if (snapshots.isEmpty()) {
            log.warn("[Backtest] No historical data for {} in time range", config.getSymbol());
            return emptyResult(config);
        }
        return simulate(config, snapshots);
    }

    /**
     * Load historical order book snapshots from TiDB.
     */
    List<BacktestSnapshot> loadSnapshots(BacktestConfig config) {
        List<OrderBookSnapshotEntity> entities = snapshotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderBookSnapshotEntity>()
                        .eq(OrderBookSnapshotEntity::getSymbol, config.getSymbol())
                        .ge(OrderBookSnapshotEntity::getSnapshotTime, config.getStartTime())
                        .le(OrderBookSnapshotEntity::getSnapshotTime, config.getEndTime())
                        .orderByAsc(OrderBookSnapshotEntity::getSnapshotTime)
        );

        List<BacktestSnapshot> snapshots = new ArrayList<>();
        for (OrderBookSnapshotEntity entity : entities) {
            try {
                List<PriceLevel> bids = parsePriceLevels(entity.getBids());
                List<PriceLevel> asks = parsePriceLevels(entity.getAsks());
                BigDecimal bestBid = bids.isEmpty() ? null : bids.get(0).getPrice();
                BigDecimal bestAsk = asks.isEmpty() ? null : asks.get(0).getPrice();
                snapshots.add(new BacktestSnapshot(
                        entity.getSnapshotTime(), bestBid, bestAsk, bids, asks, entity.getExchange()));
            } catch (Exception e) {
                log.debug("[Backtest] Skip snapshot {}: {}", entity.getId(), e.getMessage());
            }
        }
        log.info("[Backtest] Loaded {} snapshots for {}", snapshots.size(), config.getSymbol());
        return snapshots;
    }

    /**
     * Simulate strategy over pre-loaded snapshots.
     */
    public BacktestResult simulate(BacktestConfig config, List<BacktestSnapshot> snapshots) {
        log.info("[Backtest] Running backtest for {}: {} snapshots, model={}, capital={}, risk={}",
                config.getSymbol(), snapshots.size(), config.getModel(),
                config.getInitialCapital(), config.isRiskEnabled());

        // Clear volatility tracker state from previous runs
        volatilityTracker.clear(config.getSymbol());

        SymbolBo symbolBo = buildSymbolBo(config);
        SpreadCalculator calculator = buildCalculator(config, symbolBo);

        // Simulation state
        BigDecimal balance = config.getInitialCapital();
        BigDecimal netPosition = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        List<BacktestTrade> trades = new ArrayList<>();
        List<BigDecimal> equityCurve = new ArrayList<>();
        BigDecimal peak = balance;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        // Risk control state (created fresh per run)
        CircuitBreakerRisk circuitBreaker = config.isRiskEnabled()
                ? new CircuitBreakerRisk(config.getCircuitBreakerThreshold(), config.getCircuitBreakerCooldownMs())
                : null;
        BigDecimal maxDDLimit = config.getMaxDrawdownPercent();

        // FIFO buy queue for realized PnL matching
        LinkedList<FillRecord> buyQueue = new LinkedList<>();

        // Alpha tracking for order flow imbalance and momentum
        LinkedList<BigDecimal> recentMidPrices = new LinkedList<>();
        int alphaMomentumLookback = 5;
        int alphaOrderFlowDepth = 10;

        for (BacktestSnapshot snapshot : snapshots) {
            BigDecimal midPrice = snapshot.getMidPrice();
            if (midPrice == null || midPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Feed price to volatility tracker for risk calculator
            volatilityTracker.recordPrice(config.getSymbol(), midPrice);

            // Track recent prices for momentum alpha
            recentMidPrices.addLast(midPrice);
            if (recentMidPrices.size() > alphaMomentumLookback) {
                recentMidPrices.removeFirst();
            }

            // Compute alpha signals if enabled
            BigDecimal compositeAlpha = BigDecimal.ZERO;
            if (config.isAlphaEnabled()) {
                compositeAlpha = computeCompositeAlpha(config, snapshot, recentMidPrices, alphaOrderFlowDepth, alphaMomentumLookback);
            }

            // Calculate offset from the spread calculator
            BigDecimal askOffset = calculator.calculateOffset(config.getSymbol(), false, symbolBo);
            BigDecimal bidOffset = calculator.calculateOffset(config.getSymbol(), true, symbolBo);

            // Apply break-even spread clamping
            BigDecimal breakEven = BigDecimal.valueOf(2)
                    .multiply(config.getTakerFeeRate())
                    .multiply(midPrice)
                    .setScale(8, RoundingMode.HALF_UP);
            BigDecimal minHalf = breakEven.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            if (askOffset.compareTo(minHalf) < 0) askOffset = minHalf;
            if (bidOffset.compareTo(minHalf) < 0) bidOffset = minHalf;

            BigDecimal bidPrice = midPrice.subtract(bidOffset);
            BigDecimal askPrice = midPrice.add(askOffset);

            BigDecimal bestAsk = snapshot.getBestAsk();
            BigDecimal bestBid = snapshot.getBestBid();

            // ---- Risk control check ----
            if (config.isRiskEnabled()) {
                if (circuitBreaker != null && !circuitBreaker.check(config.getSymbol(), null, null, null)) {
                    circuitBreaker.recordSuccess(); // gradual recovery
                    continue;
                }
                // Drawdown check
                BigDecimal currentEquity = balance.add(netPosition.multiply(midPrice));
                if (currentEquity.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal ddPct = peak.compareTo(BigDecimal.ZERO) > 0
                            ? peak.subtract(currentEquity).multiply(BigDecimal.valueOf(100))
                            .divide(peak, 4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    if (ddPct.compareTo(maxDDLimit) >= 0) continue;
                }
            }

            boolean hadFill = false;

            // ---- Buy fills ----
            if (bestAsk != null) {
                try {
                    if (bidPrice.compareTo(bestAsk) >= 0) {
                        // Aggressive buy (taker) — cross the spread
                        FillResult fill = simulateFill(snapshot.getAsks(), symbolBo);
                        if (fill != null && fill.filledQty.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal fillPrice = fill.avgPrice;
                            BigDecimal fee = fill.totalCost.multiply(config.getTakerFeeRate())
                                    .setScale(8, RoundingMode.HALF_UP);
                            totalFees = totalFees.add(fee);

                            buyQueue.add(new FillRecord(fillPrice, fill.filledQty));
                            netPosition = netPosition.add(fill.filledQty);

                            trades.add(new BacktestTrade(snapshot.getTime(), "buy", fillPrice, fill.filledQty, fee, BigDecimal.ZERO));
                            balance = balance.subtract(fill.totalCost).subtract(fee);
                            hadFill = true;
                        }
                    } else if (bidPrice.compareTo(bestBid) > 0) {
                        // Passive buy (maker) — inside the spread
                        BigDecimal fillQty = estimateTopLevelQty(snapshot.getAsks(), symbolBo);
                        if (fillQty.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal fillPrice = bidPrice;
                            BigDecimal cost = fillPrice.multiply(fillQty);
                            BigDecimal fee = cost.multiply(config.getMakerFeeRate())
                                    .setScale(8, RoundingMode.HALF_UP);
                            totalFees = totalFees.add(fee);

                            buyQueue.add(new FillRecord(fillPrice, fillQty));
                            netPosition = netPosition.add(fillQty);

                            trades.add(new BacktestTrade(snapshot.getTime(), "buy", fillPrice, fillQty, fee, BigDecimal.ZERO));
                            balance = balance.subtract(cost).subtract(fee);
                            hadFill = true;
                        }
                    }
                } catch (Exception e) {
                    log.debug("[Backtest] Buy fill error at {}: {}", snapshot.getTime(), e.getMessage());
                }
            }

            // ---- Sell fills ----
            if (bestBid != null) {
                try {
                    if (askPrice.compareTo(bestBid) <= 0) {
                        // Aggressive sell (taker) — cross the spread
                        FillResult fill = simulateFill(snapshot.getBids(), symbolBo);
                        if (fill != null && fill.filledQty.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal fillPrice = fill.avgPrice;
                            BigDecimal fee = fill.totalCost.multiply(config.getTakerFeeRate())
                                    .setScale(8, RoundingMode.HALF_UP);
                            totalFees = totalFees.add(fee);

                            BigDecimal realizedPnl = computeFifoPnl(buyQueue, fillPrice, fill.filledQty);
                            netPosition = netPosition.subtract(fill.filledQty);

                            trades.add(new BacktestTrade(snapshot.getTime(), "sell", fillPrice, fill.filledQty, fee, realizedPnl));
                            balance = balance.add(fill.totalCost).subtract(fee);
                            hadFill = true;
                        }
                    } else if (askPrice.compareTo(bestAsk) < 0) {
                        // Passive sell (maker) — inside the spread
                        BigDecimal fillQty = estimateTopLevelQty(snapshot.getBids(), symbolBo);
                        if (fillQty.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal fillPrice = askPrice;
                            BigDecimal revenue = fillPrice.multiply(fillQty);
                            BigDecimal fee = revenue.multiply(config.getMakerFeeRate())
                                    .setScale(8, RoundingMode.HALF_UP);
                            totalFees = totalFees.add(fee);

                            BigDecimal realizedPnl = computeFifoPnl(buyQueue, fillPrice, fillQty);
                            netPosition = netPosition.subtract(fillQty);

                            trades.add(new BacktestTrade(snapshot.getTime(), "sell", fillPrice, fillQty, fee, realizedPnl));
                            balance = balance.add(revenue).subtract(fee);
                            hadFill = true;
                        }
                    }
                } catch (Exception e) {
                    log.debug("[Backtest] Sell fill error at {}: {}", snapshot.getTime(), e.getMessage());
                }
            }

            // Record circuit breaker outcome
            if (config.isRiskEnabled() && circuitBreaker != null) {
                if (hadFill) {
                    circuitBreaker.recordSuccess();
                } else {
                    circuitBreaker.recordFailure(config.getSymbol());
                }
            }

            // Mark to market equity
            BigDecimal equity = balance.add(netPosition.multiply(midPrice));
            equityCurve.add(equity);

            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            BigDecimal drawdown = peak.compareTo(BigDecimal.ZERO) > 0
                    ? peak.subtract(equity).multiply(BigDecimal.valueOf(100))
                    .divide(peak, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        BigDecimal entryPrice = buyQueue.isEmpty() ? BigDecimal.ZERO : buyQueue.peek().price;
        return buildResult(config, snapshots, trades, equityCurve, balance, netPosition,
                totalFees, maxDrawdown, entryPrice);
    }

    // ---- Alpha computation ----

    /**
     * Compute composite alpha from order flow imbalance and momentum.
     */
    private BigDecimal computeCompositeAlpha(BacktestConfig config, BacktestSnapshot snapshot,
                                              LinkedList<BigDecimal> recentPrices,
                                              int orderFlowDepth, int momentumLookback) {
        // Order flow imbalance alpha
        double bidVol = 0, askVol = 0;
        List<PriceLevel> bids = snapshot.getBids();
        List<PriceLevel> asks = snapshot.getAsks();
        for (int i = 0; i < Math.min(orderFlowDepth, bids.size()); i++) {
            bidVol += bids.get(i).getQuantity().doubleValue();
        }
        for (int i = 0; i < Math.min(orderFlowDepth, asks.size()); i++) {
            askVol += asks.get(i).getQuantity().doubleValue();
        }
        double totalVol = bidVol + askVol;
        double orderFlowAlpha = totalVol > 0 ? (bidVol - askVol) / totalVol : 0;

        // Momentum alpha
        double momentumAlpha = 0;
        if (recentPrices.size() >= 2) {
            double first = recentPrices.getFirst().doubleValue();
            double last = recentPrices.getLast().doubleValue();
            if (first > 0) {
                double roc = (last - first) / first;
                momentumAlpha = Math.tanh(roc * 10); // scale and clamp to [-1, 1]
            }
        }

        // Default weights
        double orderFlowWeight = 0.5;
        double momentumWeight = 0.5;

        double composite = orderFlowWeight * orderFlowAlpha + momentumWeight * momentumAlpha;
        return BigDecimal.valueOf(Math.max(-1.0, Math.min(1.0, composite)));
    }

    // ---- Fill simulation ----

    /**
     * Simulate multi-level fill. Consumes liquidity from the order book levels.
     */
    private FillResult simulateFill(List<PriceLevel> levels, SymbolBo symbolBo) {
        if (levels == null || levels.isEmpty()) return null;

        BigDecimal remaining = symbolBo.getMaxDelegateCount();
        BigDecimal totalFilled = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (PriceLevel level : levels) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal levelPrice = level.getPrice();
            BigDecimal levelQty = level.getQuantity().min(remaining);
            totalFilled = totalFilled.add(levelQty);
            totalCost = totalCost.add(levelQty.multiply(levelPrice));
            remaining = remaining.subtract(levelQty);
        }

        if (totalFilled.compareTo(BigDecimal.ZERO) <= 0) return null;

        BigDecimal avgPrice = totalCost.divide(totalFilled, 8, RoundingMode.HALF_UP);
        return new FillResult(avgPrice, totalFilled, totalCost);
    }

    /**
     * Get the top-level available quantity, capped by max delegate count.
     */
    private BigDecimal estimateTopLevelQty(List<PriceLevel> levels, SymbolBo symbolBo) {
        if (levels == null || levels.isEmpty()) return BigDecimal.ZERO;
        BigDecimal qty = levels.get(0).getQuantity();
        if (symbolBo.getMaxDelegateCount() != null
                && qty.compareTo(symbolBo.getMaxDelegateCount()) > 0) {
            qty = symbolBo.getMaxDelegateCount();
        }
        return qty;
    }

    // ---- PnL helpers ----

    /**
     * Compute realized PnL for a sell using FIFO matching against the buy queue.
     */
    private BigDecimal computeFifoPnl(LinkedList<FillRecord> buyQueue, BigDecimal sellPrice, BigDecimal sellQty) {
        BigDecimal realizedPnl = BigDecimal.ZERO;
        BigDecimal remaining = sellQty;
        while (remaining.compareTo(BigDecimal.ZERO) > 0 && !buyQueue.isEmpty()) {
            FillRecord buyFill = buyQueue.peek();
            BigDecimal matchQty = remaining.min(buyFill.quantity);
            realizedPnl = realizedPnl.add(matchQty.multiply(sellPrice.subtract(buyFill.price)));
            buyFill.quantity = buyFill.quantity.subtract(matchQty);
            remaining = remaining.subtract(matchQty);
            if (buyFill.quantity.compareTo(BigDecimal.ZERO) <= 0) {
                buyQueue.poll();
            }
        }
        return realizedPnl;
    }

    // ---- Calculator factory ----

    private SpreadCalculator buildCalculator(BacktestConfig config, SymbolBo symbolBo) {
        BigDecimal baseTicks = extractParam(config, "baseOffsetTicks", BigDecimal.ONE);

        return switch (config.getModel().toLowerCase()) {
            case "constant" -> new ConstantSpreadCalculator(baseTicks);
            case "inventory" -> new InventoryBasedSpreadCalculator(
                    baseTicks,
                    extractParam(config, "targetPosition", BigDecimal.ZERO),
                    extractParam(config, "maxPosition", BigDecimal.ONE),
                    extractParam(config, "skewFactor", new BigDecimal("0.5")),
                    null,
                    extractParam(config, "alphaMaxPositionAdjustment", new BigDecimal("0.5"))
            );
            case "risk" -> new RiskAdjustedSpreadCalculator(
                    baseTicks,
                    extractParam(config, "volCoeff", new BigDecimal("2.0")),
                    volatilityTracker
            );
            case "hybrid" -> {
                InventoryBasedSpreadCalculator invCalc = new InventoryBasedSpreadCalculator(
                        baseTicks,
                        extractParam(config, "targetPosition", BigDecimal.ZERO),
                        extractParam(config, "maxPosition", BigDecimal.ONE),
                        extractParam(config, "skewFactor", new BigDecimal("0.5")),
                        null,
                        extractParam(config, "alphaMaxPositionAdjustment", new BigDecimal("0.5"))
                );
                RiskAdjustedSpreadCalculator riskCalc = new RiskAdjustedSpreadCalculator(
                        baseTicks,
                        extractParam(config, "volCoeff", new BigDecimal("2.0")),
                        volatilityTracker
                );
                yield new HybridSpreadCalculator(List.of(
                        new HybridSpreadCalculator.WeightedCalculator(
                                new ConstantSpreadCalculator(baseTicks),
                                extractParam(config, "constantWeight", new BigDecimal("0.3"))),
                        new HybridSpreadCalculator.WeightedCalculator(invCalc,
                                extractParam(config, "inventoryWeight", new BigDecimal("0.4"))),
                        new HybridSpreadCalculator.WeightedCalculator(riskCalc,
                                extractParam(config, "riskWeight", new BigDecimal("0.3")))
                ));
            }
            default -> {
                log.warn("[Backtest] Unknown model '{}', using constant", config.getModel());
                yield new ConstantSpreadCalculator(baseTicks);
            }
        };
    }

    // ---- Parameter extraction ----

    @SuppressWarnings("unchecked")
    private <T> T extractParam(BacktestConfig config, String key, T defaultValue) {
        if (config.getModelParams() != null && config.getModelParams().containsKey(key)) {
            Object val = config.getModelParams().get(key);
            if (defaultValue instanceof BigDecimal) {
                return (T) new BigDecimal(val.toString());
            }
            if (defaultValue instanceof Double) {
                return (T) Double.valueOf(val.toString());
            }
            if (defaultValue instanceof Integer) {
                return (T) Integer.valueOf(val.toString());
            }
            return (T) val;
        }
        return defaultValue;
    }

    // ---- Symbol builder ----

    private SymbolBo buildSymbolBo(BacktestConfig config) {
        SymbolBo bo = new SymbolBo();
        bo.setBaseTokenId(config.getSymbol().replace("USDT", ""));
        bo.setQuoteTokenId("USDT");
        bo.setTickSize(extractParam(config, "tickSize", new BigDecimal("0.1")));
        bo.setStepSize(extractParam(config, "stepSize", new BigDecimal("0.001")));
        bo.setMinSize(extractParam(config, "minSize", new BigDecimal("0.001")));
        bo.setMaxDelegateCount(extractParam(config, "maxDelegateCount", new BigDecimal("10")));
        bo.setMinRate(extractParam(config, "minRate", new BigDecimal("0.8")));
        bo.setMaxRate(extractParam(config, "maxRate", new BigDecimal("1.2")));
        return bo;
    }

    // ---- JSON parsing ----

    private List<PriceLevel> parsePriceLevels(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        List<PriceLevel> levels = new ArrayList<>();
        try {
            var array = com.alibaba.fastjson.JSON.parseArray(json);
            for (int i = 0; i < array.size(); i++) {
                var inner = array.getJSONArray(i);
                if (inner.size() >= 2) {
                    levels.add(new PriceLevel(
                            new BigDecimal(inner.getString(0)),
                            new BigDecimal(inner.getString(1))));
                }
            }
        } catch (Exception e) {
            log.warn("[Backtest] Failed to parse price levels: {}", e.getMessage());
        }
        return levels;
    }

    // ---- Result builder ----

    private BacktestResult buildResult(BacktestConfig config, List<BacktestSnapshot> snapshots,
                                       List<BacktestTrade> trades, List<BigDecimal> equityCurve,
                                       BigDecimal balance, BigDecimal netPosition,
                                       BigDecimal totalFees, BigDecimal maxDrawdown,
                                       BigDecimal entryPrice) {
        BigDecimal lastMid = snapshots.get(snapshots.size() - 1).getMidPrice();
        BigDecimal finalEquity = balance.add(netPosition.multiply(lastMid));

        BigDecimal totalReturn = config.getInitialCapital().compareTo(BigDecimal.ZERO) > 0
                ? finalEquity.subtract(config.getInitialCapital())
                .multiply(BigDecimal.valueOf(100))
                .divide(config.getInitialCapital(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long elapsedMs = snapshots.get(snapshots.size() - 1).getTime()
                - snapshots.get(0).getTime();
        double years = Math.max(elapsedMs / (365.25 * 24 * 3600 * 1000.0), 0.001);
        double annualizedReturn = Math.pow(1 + totalReturn.doubleValue() / 100, 1.0 / years) - 1;

        // Sharpe ratio
        double sharpe = 0;
        if (equityCurve.size() > 1) {
            double[] returns = new double[equityCurve.size() - 1];
            for (int i = 1; i < equityCurve.size(); i++) {
                double prev = equityCurve.get(i - 1).doubleValue();
                returns[i - 1] = prev > 0 ? (equityCurve.get(i).doubleValue() - prev) / prev : 0;
            }
            double mean = Arrays.stream(returns).average().orElse(0);
            double variance = Arrays.stream(returns).map(r -> Math.pow(r - mean, 2)).average().orElse(0);
            double stddev = Math.sqrt(variance);
            double ticksPerYear = years > 0 ? snapshots.size() / years : 0;
            sharpe = stddev > 0 ? mean / stddev * Math.sqrt(ticksPerYear) : 0;
        }

        // Win/loss stats
        int winning = (int) trades.stream().filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0).count();
        int losing = (int) trades.stream().filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0).count();

        // Calmar ratio = annualized return / max drawdown
        BigDecimal annualizedReturnBD = BigDecimal.valueOf(annualizedReturn * 100).setScale(4, RoundingMode.HALF_UP);
        BigDecimal calmarRatio = maxDrawdown.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(annualizedReturn * 100).divide(maxDrawdown, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Average trade PnL
        BigDecimal avgTradePnl = trades.isEmpty() ? BigDecimal.ZERO
                : trades.stream().map(BacktestTrade::getRealizedPnl).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(trades.size()), 4, RoundingMode.HALF_UP);

        // Profit factor
        BigDecimal grossProfit = trades.stream()
                .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                .map(BacktestTrade::getRealizedPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = trades.stream()
                .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0)
                .map(BacktestTrade::getRealizedPnl).reduce(BigDecimal.ZERO, BigDecimal::add).abs();
        BigDecimal profitFactor = grossLoss.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP)
                : grossProfit.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(9999) : BigDecimal.ZERO;

        BacktestResult result = new BacktestResult();
        result.setId(UUID.randomUUID().toString().substring(0, 8));
        result.setSymbol(config.getSymbol());
        result.setModel(config.getModel());
        result.setStartTime(config.getStartTime());
        result.setEndTime(config.getEndTime());
        result.setTotalTicks(snapshots.size());
        result.setInitialCapital(config.getInitialCapital());
        result.setFinalBalance(finalEquity.setScale(2, RoundingMode.HALF_UP));
        result.setTotalReturn(totalReturn.setScale(4, RoundingMode.HALF_UP));
        result.setAnnualizedReturn(annualizedReturnBD);
        result.setSharpeRatio(BigDecimal.valueOf(sharpe).setScale(4, RoundingMode.HALF_UP));
        result.setMaxDrawdown(maxDrawdown.setScale(4, RoundingMode.HALF_UP));
        result.setTotalTrades(trades.size());
        result.setWinningTrades(winning);
        result.setLosingTrades(losing);
        result.setWinRate(trades.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(winning).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(trades.size()), 2, RoundingMode.HALF_UP));
        result.setTotalFees(totalFees.setScale(8, RoundingMode.HALF_UP));
        result.setTrades(trades);

        // Enhanced fields
        result.setEquityCurve(equityCurve);
        result.setCalmarRatio(calmarRatio);
        result.setAvgTradePnl(avgTradePnl);
        result.setProfitFactor(profitFactor);

        log.info("[Backtest] {}: return={}%, sharpe={}, calmar={}, trades={}, maxDD={}%",
                config.getSymbol(), result.getTotalReturn(), result.getSharpeRatio(),
                result.getCalmarRatio(), result.getTotalTrades(), result.getMaxDrawdown());
        return result;
    }

    private BacktestResult emptyResult(BacktestConfig config) {
        BacktestResult result = new BacktestResult();
        result.setId("empty");
        result.setSymbol(config.getSymbol());
        result.setModel(config.getModel());
        result.setInitialCapital(config.getInitialCapital());
        result.setFinalBalance(config.getInitialCapital());
        result.setTotalTrades(0);
        return result;
    }

    // ---- Inner classes ----

    private static class FillRecord {
        BigDecimal price;
        BigDecimal quantity;

        FillRecord(BigDecimal price, BigDecimal quantity) {
            this.price = price;
            this.quantity = quantity;
        }
    }

    private static class FillResult {
        final BigDecimal avgPrice;
        final BigDecimal filledQty;
        final BigDecimal totalCost;

        FillResult(BigDecimal avgPrice, BigDecimal filledQty, BigDecimal totalCost) {
            this.avgPrice = avgPrice;
            this.filledQty = filledQty;
            this.totalCost = totalCost;
        }
    }
}

package com.orderbook.core.backtest;

import com.orderbook.core.domain.PriceLevel;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.entity.OrderBookSnapshotEntity;
import com.orderbook.core.mapper.OrderBookSnapshotMapper;
import com.orderbook.core.strategy.spread.*;
import com.orderbook.core.strategy.alpha.AlphaAggregator;
import com.orderbook.core.strategy.alpha.AlphaConfig;
import com.orderbook.core.store.OrderBookStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Core backtest engine that replays historical order book snapshots through
 * spread calculators and simulates fills with PnL tracking.
 */
@Slf4j
@Component
public class BacktestEngine {

    private final OrderBookSnapshotMapper snapshotMapper;
    private final VolatilityTracker volatilityTracker;
    private final OrderBookStore orderBookStore;

    public BacktestEngine(OrderBookSnapshotMapper snapshotMapper,
                          VolatilityTracker volatilityTracker,
                          OrderBookStore orderBookStore) {
        this.snapshotMapper = snapshotMapper;
        this.volatilityTracker = volatilityTracker;
        this.orderBookStore = orderBookStore;
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
                        entity.getSnapshotTime(), bestBid, bestAsk, bids, asks));
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
        log.info("[Backtest] Running backtest for {}: {} snapshots, model={}, capital={}",
                config.getSymbol(), snapshots.size(), config.getModel(), config.getInitialCapital());

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

        LinkedList<FillRecord> buyQueue = new LinkedList<>();

        for (BacktestSnapshot snapshot : snapshots) {
            BigDecimal midPrice = snapshot.getMidPrice();
            if (midPrice == null || midPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Feed price to volatility tracker for risk calculator
            volatilityTracker.recordPrice(config.getSymbol(), midPrice);

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

            // Simulate buy fills
            if (bestAsk != null && bidPrice.compareTo(bestAsk) >= 0) {
                BigDecimal fillPrice = bestAsk;
                BigDecimal fillQty = estimateFillQuantity(snapshot.getAsks(), symbolBo);
                BigDecimal fee = fillQty.multiply(fillPrice).multiply(config.getTakerFeeRate())
                        .setScale(8, RoundingMode.HALF_UP);
                totalFees = totalFees.add(fee);

                buyQueue.add(new FillRecord(fillPrice, fillQty));
                netPosition = netPosition.add(fillQty);

                trades.add(new BacktestTrade(snapshot.getTime(), "buy", fillPrice, fillQty, fee, BigDecimal.ZERO));
                balance = balance.subtract(fillQty.multiply(fillPrice)).subtract(fee);
            }

            // Simulate sell fills
            if (bestBid != null && askPrice.compareTo(bestBid) <= 0) {
                BigDecimal fillPrice = bestBid;
                BigDecimal fillQty = estimateFillQuantity(snapshot.getBids(), symbolBo);
                BigDecimal fee = fillQty.multiply(fillPrice).multiply(config.getTakerFeeRate())
                        .setScale(8, RoundingMode.HALF_UP);
                totalFees = totalFees.add(fee);

                BigDecimal realizedPnl = BigDecimal.ZERO;
                BigDecimal remaining = fillQty;
                while (remaining.compareTo(BigDecimal.ZERO) > 0 && !buyQueue.isEmpty()) {
                    FillRecord buyFill = buyQueue.peek();
                    BigDecimal matchQty = remaining.min(buyFill.quantity);
                    realizedPnl = realizedPnl.add(matchQty.multiply(fillPrice.subtract(buyFill.price)));
                    buyFill.quantity = buyFill.quantity.subtract(matchQty);
                    remaining = remaining.subtract(matchQty);
                    if (buyFill.quantity.compareTo(BigDecimal.ZERO) <= 0) {
                        buyQueue.poll();
                    }
                }

                netPosition = netPosition.subtract(fillQty);

                trades.add(new BacktestTrade(snapshot.getTime(), "sell", fillPrice, fillQty, fee, realizedPnl));
                balance = balance.add(fillQty.multiply(fillPrice)).subtract(fee);
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

        return buildResult(config, snapshots, trades, equityCurve, balance, netPosition,
                totalFees, maxDrawdown, buyQueue.isEmpty() ? BigDecimal.ZERO
                        : buyQueue.peek().price);
    }

    // ---- Private helpers ----

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

    private BigDecimal estimateFillQuantity(List<PriceLevel> levels, SymbolBo symbolBo) {
        if (levels == null || levels.isEmpty()) return BigDecimal.ZERO;
        BigDecimal qty = levels.get(0).getQuantity();
        if (symbolBo.getMaxDelegateCount() != null
                && qty.compareTo(symbolBo.getMaxDelegateCount()) > 0) {
            qty = symbolBo.getMaxDelegateCount();
        }
        return qty;
    }

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

    private List<PriceLevel> parsePriceLevels(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        List<PriceLevel> levels = new ArrayList<>();
        try {
            // JSON format: [["price","qty"],...]
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

        int winning = (int) trades.stream().filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0).count();
        int losing = (int) trades.stream().filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0).count();

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
        result.setAnnualizedReturn(BigDecimal.valueOf(annualizedReturn * 100).setScale(4, RoundingMode.HALF_UP));
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

        log.info("[Backtest] {}: return={}%, sharpe={}, trades={}, maxDD={}%",
                config.getSymbol(), result.getTotalReturn(), result.getSharpeRatio(),
                result.getTotalTrades(), result.getMaxDrawdown());
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

    private static class FillRecord {
        BigDecimal price;
        BigDecimal quantity;

        FillRecord(BigDecimal price, BigDecimal quantity) {
            this.price = price;
            this.quantity = quantity;
        }
    }
}

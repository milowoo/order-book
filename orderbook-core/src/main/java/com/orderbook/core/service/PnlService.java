package com.orderbook.core.service;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.PnlSnapshot;
import com.orderbook.core.domain.TradeRecord;
import com.orderbook.core.store.SymbolStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 基于先进先出成本法的内存盈亏跟踪服务。
 * 跟踪每个交易标的的成交记录，并计算已实现/未实现盈亏及手续费。
 */
@Slf4j
@Service
public class PnlService {

    private static final int LOG_INTERVAL_TICKS = 60;
    private final AtomicInteger tickCounter = new AtomicInteger(0);

    private final Map<String, List<TradeRecord>> tradesBySymbol = new ConcurrentHashMap<>();
    private final Map<String, PnlSnapshot> snapshots = new ConcurrentHashMap<>();
    // FIFO buy queue per symbol for realized PnL
    private final Map<String, Queue<TradeRecord>> buyQueue = new ConcurrentHashMap<>();
    // Current net position per symbol
    private final Map<String, BigDecimal> netPosition = new ConcurrentHashMap<>();
    // Average entry price per symbol
    private final Map<String, BigDecimal> entryPrice = new ConcurrentHashMap<>();

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private SymbolStore symbolStore;

    @PostConstruct
    public void init() {
        log.info("PnlService initialized, attempting to restore inventory from DB...");
        try {
            List<com.orderbook.core.domain.SymbolBo> symbols = symbolStore.getActiveSymbols();
            if (symbols != null) {
                for (var symbolBo : symbols) {
                    restoreFromDb(symbolBo.getSymbolId(), ExchangeCode.BYBIT);
                    restoreFromDb(symbolBo.getSymbolId(), ExchangeCode.BINANCE);
                    restoreFromDb(symbolBo.getSymbolId(), ExchangeCode.BITGET);
                    restoreFromDb(symbolBo.getSymbolId(), ExchangeCode.OSL_GLOBAL);
                }
            }
        } catch (Exception e) {
            log.warn("[Pnl] Failed to restore inventory from DB: {}", e.getMessage());
        }
    }

    /**
     * Record a fill event from order update.
     */
    public synchronized void recordFill(String symbol, String side, BigDecimal price,
                                         BigDecimal quantity, BigDecimal fee,
                                         String exchange, String tradeId) {
        if (symbol == null || side == null || price == null || quantity == null) {
            return;
        }

        String id = tradeId != null ? tradeId : UUID.randomUUID().toString();
        BigDecimal amount = price.multiply(quantity).setScale(8, RoundingMode.HALF_UP);
        BigDecimal feeVal = fee != null ? fee : BigDecimal.ZERO;

        TradeRecord record = TradeRecord.builder()
                .tradeId(id)
                .symbol(symbol)
                .side(side)
                .price(price)
                .quantity(quantity)
                .amount(amount)
                .fee(feeVal)
                .feeCurrency("USDT")
                .exchange(exchange)
                .tradeTime(System.currentTimeMillis())
                .build();

        tradesBySymbol.computeIfAbsent(symbol, k -> new ArrayList<>()).add(record);

        // Update PnL
        BigDecimal realizedPnl = updateFifoPnl(symbol, side, price, quantity, feeVal);

        // Build snapshot
        BigDecimal pos = netPosition.getOrDefault(symbol, BigDecimal.ZERO);
        BigDecimal avgEntry = entryPrice.getOrDefault(symbol, BigDecimal.ZERO);
        BigDecimal totalFees = calculateTotalFees(symbol);
        BigDecimal totalVolume = calculateTotalVolume(symbol);
        int tradeCount = tradesBySymbol.get(symbol).size();

        PnlSnapshot snapshot = PnlSnapshot.builder()
                .symbol(symbol)
                .realizedPnl(realizedPnl)
                .unrealizedPnl(BigDecimal.ZERO) // updated later via updateUnrealizedPnl
                .totalFees(totalFees)
                .totalVolume(totalVolume)
                .currentPosition(pos)
                .entryPrice(avgEntry)
                .lastUpdated(System.currentTimeMillis())
                .tradeCount(tradeCount)
                .build();
        snapshots.put(symbol, snapshot);

        log.info("[Pnl] [{}] Fill {} {} @ {} qty={} fee={} | realized={} pos={}",
                symbol, side, id.substring(0, 8), price, quantity, feeVal, realizedPnl, pos);

        // Persist to database
        try {
            persistenceService.saveTradeLog(record);
            persistenceService.saveInventorySnapshot(symbol,
                    ExchangeCode.valueOf(exchange), snapshot);
        } catch (Exception e) {
            log.warn("[Pnl] Failed to persist to DB: {}", e.getMessage());
        }
    }

    /**
     * Update unrealized PnL based on current mid-price. Called each tick.
     */
    public synchronized void updateUnrealizedPnl(String symbol, BigDecimal midPrice) {
        PnlSnapshot current = snapshots.get(symbol);
        if (current == null) return;

        BigDecimal pos = current.getCurrentPosition();
        BigDecimal unrealized;
        if (pos.compareTo(BigDecimal.ZERO) != 0 && midPrice != null
                && current.getEntryPrice().compareTo(BigDecimal.ZERO) > 0) {
            unrealized = (midPrice.subtract(current.getEntryPrice()))
                    .multiply(pos)
                    .setScale(8, RoundingMode.HALF_UP);
        } else {
            unrealized = BigDecimal.ZERO;
        }

        PnlSnapshot updated = PnlSnapshot.builder()
                .symbol(current.getSymbol())
                .realizedPnl(current.getRealizedPnl())
                .unrealizedPnl(unrealized)
                .totalFees(current.getTotalFees())
                .totalVolume(current.getTotalVolume())
                .currentPosition(current.getCurrentPosition())
                .entryPrice(current.getEntryPrice())
                .lastUpdated(System.currentTimeMillis())
                .tradeCount(current.getTradeCount())
                .build();
        snapshots.put(symbol, updated);
    }

    public PnlSnapshot getSnapshot(String symbol) {
        return snapshots.get(symbol);
    }

    public Map<String, PnlSnapshot> getAllSnapshots() {
        return Collections.unmodifiableMap(snapshots);
    }

    public List<TradeRecord> getTrades(String symbol) {
        return tradesBySymbol.getOrDefault(symbol, Collections.emptyList());
    }

    /**
     * Log PnL summary. Call from strategy each tick.
     */
    public void logSummary() {
        int tick = tickCounter.incrementAndGet();
        if (tick % LOG_INTERVAL_TICKS != 0) return;

        if (snapshots.isEmpty()) return;

        BigDecimal totalPnl = BigDecimal.ZERO;
        for (Map.Entry<String, PnlSnapshot> entry : snapshots.entrySet()) {
            PnlSnapshot s = entry.getValue();
            BigDecimal total = s.getRealizedPnl().add(s.getUnrealizedPnl());
            totalPnl = totalPnl.add(total);
            log.info("[Pnl-Summary] [{}] realized={} unrealized={} total={} fees={} pos={} trades={}",
                    s.getSymbol(), s.getRealizedPnl(), s.getUnrealizedPnl(),
                    total, s.getTotalFees(), s.getCurrentPosition(), s.getTradeCount());
        }
        log.info("[Pnl-Summary] Portfolio total PnL={}", totalPnl);
    }

    // ---- Inventory restore from DB ----

    /**
     * Restore inventory state from the latest DB snapshot for a symbol+exchange.
     * This allows position recovery after application restart.
     */
    public synchronized void restoreFromDb(String symbol, ExchangeCode exchange) {
        try {
            var entity = persistenceService.loadLatestInventory(symbol, exchange);
            if (entity == null) {
                log.debug("[Pnl] No saved inventory for {}/{}", symbol, exchange);
                return;
            }

            BigDecimal pos = entity.getNetPosition() != null ? entity.getNetPosition() : BigDecimal.ZERO;
            BigDecimal ep = entity.getEntryPrice() != null ? entity.getEntryPrice() : BigDecimal.ZERO;
            if (pos.compareTo(BigDecimal.ZERO) != 0) {
                netPosition.put(symbol, pos);
                if (entity.getEntryPrice() != null) {
                    entryPrice.put(symbol, entity.getEntryPrice());
                }

                // Rebuild buy FIFO queue from entry price
                Queue<TradeRecord> queue = buyQueue.computeIfAbsent(symbol, k -> new LinkedList<>());
                queue.clear();
                if (pos.compareTo(BigDecimal.ZERO) > 0) {
                    queue.add(new TradeRecord(null, symbol, "buy", ep, pos,
                            ep.multiply(pos), BigDecimal.ZERO, "USDT", exchange.name(), System.currentTimeMillis()));
                }

                PnlSnapshot snapshot = PnlSnapshot.builder()
                        .symbol(symbol)
                        .realizedPnl(entity.getRealizedPnl() != null ? entity.getRealizedPnl() : BigDecimal.ZERO)
                        .unrealizedPnl(BigDecimal.ZERO)
                        .totalFees(entity.getTotalFees() != null ? entity.getTotalFees() : BigDecimal.ZERO)
                        .totalVolume(entity.getTotalVolume() != null ? entity.getTotalVolume() : BigDecimal.ZERO)
                        .currentPosition(pos)
                        .entryPrice(ep)
                        .lastUpdated(System.currentTimeMillis())
                        .tradeCount(entity.getTradeCount() != null ? entity.getTradeCount() : 0)
                        .build();
                snapshots.put(symbol, snapshot);

                log.info("[Pnl] Restored {}/{}: pos={}, entry={}, realized={}, trades={}",
                        symbol, exchange, pos, entity.getEntryPrice(),
                        entity.getRealizedPnl(), entity.getTradeCount());
            }
        } catch (Exception e) {
            log.warn("[Pnl] Failed to restore inventory for {}/{}: {}", symbol, exchange, e.getMessage());
        }
    }

    // ---- Private helpers ----

    private BigDecimal updateFifoPnl(String symbol, String side, BigDecimal price,
                                      BigDecimal quantity, BigDecimal fee) {
        Queue<TradeRecord> queue = buyQueue.computeIfAbsent(symbol, k -> new LinkedList<>());
        BigDecimal pos = netPosition.getOrDefault(symbol, BigDecimal.ZERO);
        BigDecimal totalRealized = BigDecimal.ZERO;

        if ("buy".equalsIgnoreCase(side)) {
            // Add to queue
            queue.add(new TradeRecord(null, symbol, "buy", price, quantity,
                    price.multiply(quantity), BigDecimal.ZERO, "USDT", "", System.currentTimeMillis()));
            // Update position
            pos = pos.add(quantity);
            netPosition.put(symbol, pos);
            // Update entry price (weighted average)
            BigDecimal avgEntry = entryPrice.getOrDefault(symbol, BigDecimal.ZERO);
            if (avgEntry.compareTo(BigDecimal.ZERO) > 0 && pos.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalCost = avgEntry.multiply(pos.subtract(quantity))
                        .add(price.multiply(quantity));
                avgEntry = totalCost.divide(pos, 8, RoundingMode.HALF_UP);
            } else {
                avgEntry = price;
            }
            entryPrice.put(symbol, avgEntry);
            // Fee reduces realized PnL
            totalRealized = totalRealized.subtract(fee);
        } else if ("sell".equalsIgnoreCase(side)) {
            BigDecimal remainingQty = quantity;
            // Match against buy queue (FIFO)
            while (remainingQty.compareTo(BigDecimal.ZERO) > 0 && !queue.isEmpty()) {
                TradeRecord buy = queue.peek();
                BigDecimal matchQty = buy.getQuantity().min(remainingQty);
                BigDecimal buyPrice = buy.getPrice();

                BigDecimal tradePnl = price.subtract(buyPrice)
                        .multiply(matchQty)
                        .setScale(8, RoundingMode.HALF_UP);
                totalRealized = totalRealized.add(tradePnl);

                if (matchQty.compareTo(buy.getQuantity()) >= 0) {
                    queue.poll(); // fully consumed
                } else {
                    // Partially consume
                    queue.peek().setQuantity(buy.getQuantity().subtract(matchQty));
                }
                remainingQty = remainingQty.subtract(matchQty);
            }
            // Update position
            pos = pos.subtract(quantity);
            netPosition.put(symbol, pos);
            // Fee reduces realized PnL
            totalRealized = totalRealized.subtract(fee);

            // Update entry price
            if (pos.compareTo(BigDecimal.ZERO) > 0 && !queue.isEmpty()) {
                BigDecimal totalCost = BigDecimal.ZERO;
                BigDecimal totalQty = BigDecimal.ZERO;
                for (TradeRecord buy : queue) {
                    totalCost = totalCost.add(buy.getPrice().multiply(buy.getQuantity()));
                    totalQty = totalQty.add(buy.getQuantity());
                }
                entryPrice.put(symbol, totalCost.divide(totalQty, 8, RoundingMode.HALF_UP));
            } else if (pos.compareTo(BigDecimal.ZERO) <= 0) {
                netPosition.put(symbol, BigDecimal.ZERO);
                entryPrice.put(symbol, BigDecimal.ZERO);
                queue.clear();
            }
        }

        // Add to cumulative realized PnL
        PnlSnapshot current = snapshots.get(symbol);
        BigDecimal cumulativeRealized = totalRealized;
        if (current != null) {
            cumulativeRealized = current.getRealizedPnl().add(totalRealized);
        }
        return cumulativeRealized;
    }

    private BigDecimal calculateTotalFees(String symbol) {
        List<TradeRecord> trades = tradesBySymbol.get(symbol);
        if (trades == null || trades.isEmpty()) return BigDecimal.ZERO;
        return trades.stream()
                .map(TradeRecord::getFee)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTotalVolume(String symbol) {
        List<TradeRecord> trades = tradesBySymbol.get(symbol);
        if (trades == null || trades.isEmpty()) return BigDecimal.ZERO;
        return trades.stream()
                .map(TradeRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);
    }
}

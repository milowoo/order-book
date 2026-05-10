package com.orderbook.core.service;

import com.alibaba.fastjson.JSON;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.PriceLevel;
import com.orderbook.core.domain.PnlSnapshot;
import com.orderbook.core.domain.TradeRecord;
import com.orderbook.core.entity.InventorySnapshotEntity;
import com.orderbook.core.entity.OrderBookSnapshotEntity;
import com.orderbook.core.entity.TradeLogEntity;
import com.orderbook.core.mapper.InventorySnapshotMapper;
import com.orderbook.core.mapper.OrderBookSnapshotMapper;
import com.orderbook.core.mapper.TradeLogMapper;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.store.SymbolStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Persistence service for writing trade logs, inventory snapshots,
 * and order book snapshots to TiDB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersistenceService {

    private final TradeLogMapper tradeLogMapper;
    private final InventorySnapshotMapper inventorySnapshotMapper;
    private final OrderBookSnapshotMapper orderBookSnapshotMapper;
    private final OrderBookStore orderBookStore;
    private final SymbolStore symbolStore;

    @PostConstruct
    public void init() {
        log.info("PersistenceService initialized");
    }

    /**
     * Persist a trade log record from a fill event.
     */
    public void saveTradeLog(TradeRecord record) {
        try {
            TradeLogEntity entity = TradeLogEntity.builder()
                    .tradeId(record.getTradeId())
                    .symbol(record.getSymbol())
                    .side(record.getSide())
                    .price(record.getPrice())
                    .quantity(record.getQuantity())
                    .amount(record.getAmount())
                    .fee(record.getFee())
                    .feeCurrency(record.getFeeCurrency())
                    .exchange(record.getExchange())
                    .tradeTime(record.getTradeTime())
                    .createdAt(System.currentTimeMillis())
                    .build();
            tradeLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("[Persistence] Failed to save trade log: {}", e.getMessage());
        }
    }

    /**
     * Persist inventory snapshot. Uses INSERT ON DUPLICATE KEY UPDATE logic
     * via MyBatis-Plus. We first try to find an existing record and update,
     * or insert a new one.
     */
    public void saveInventorySnapshot(String symbol, ExchangeCode exchange, PnlSnapshot snapshot) {
        try {
            String exchangeName = exchange.name();
            InventorySnapshotEntity entity = InventorySnapshotEntity.builder()
                    .symbol(symbol)
                    .exchange(exchangeName)
                    .netPosition(snapshot.getCurrentPosition())
                    .entryPrice(snapshot.getEntryPrice())
                    .realizedPnl(snapshot.getRealizedPnl())
                    .totalFees(snapshot.getTotalFees())
                    .totalVolume(snapshot.getTotalVolume())
                    .tradeCount(snapshot.getTradeCount())
                    .snapshotTime(snapshot.getLastUpdated())
                    .updatedAt(System.currentTimeMillis())
                    .build();

            // Find existing by symbol+exchange
            var existing = inventorySnapshotMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventorySnapshotEntity>()
                            .eq(InventorySnapshotEntity::getSymbol, symbol)
                            .eq(InventorySnapshotEntity::getExchange, exchangeName)
            );

            if (existing != null && !existing.isEmpty()) {
                InventorySnapshotEntity exist = existing.get(0);
                entity.setId(exist.getId());
                inventorySnapshotMapper.updateById(entity);
            } else {
                inventorySnapshotMapper.insert(entity);
            }
        } catch (Exception e) {
            log.warn("[Persistence] Failed to save inventory snapshot: {}", e.getMessage());
        }
    }

    /**
     * Load the latest inventory snapshot for a symbol+exchange.
     * Returns null if none exists.
     */
    public InventorySnapshotEntity loadLatestInventory(String symbol, ExchangeCode exchange) {
        try {
            var results = inventorySnapshotMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventorySnapshotEntity>()
                            .eq(InventorySnapshotEntity::getSymbol, symbol)
                            .eq(InventorySnapshotEntity::getExchange, exchange.name())
                            .orderByDesc(InventorySnapshotEntity::getUpdatedAt)
                            .last("LIMIT 1")
            );
            return (results != null && !results.isEmpty()) ? results.get(0) : null;
        } catch (Exception e) {
            log.warn("[Persistence] Failed to load inventory for {}/{}: {}", symbol, exchange, e.getMessage());
            return null;
        }
    }

    /**
     * Scheduled: capture order book snapshots every 10 seconds for all active symbols.
     */
    @Scheduled(fixedDelay = 10_000)
    public void captureOrderBookSnapshot() {
        try {
            var activeSymbols = symbolStore.getActiveSymbols();
            if (activeSymbols == null || activeSymbols.isEmpty()) return;

            for (var symbolBo : activeSymbols) {
                String symbol = symbolBo.getSymbolId();
                for (ExchangeCode exchange : List.of(ExchangeCode.BYBIT, ExchangeCode.BINANCE,
                        ExchangeCode.BITGET, ExchangeCode.OSL_GLOBAL)) {
                    OrderBook book = orderBookStore.get(exchange, symbol);
                    if (book == null || book.getBid() == null || book.getAsk() == null) continue;

                    String bidsJson = JSON.toJSONString(
                            book.getBid().stream().map(pl -> List.of(pl.getPrice(), pl.getQuantity())).collect(Collectors.toList())
                    );
                    String asksJson = JSON.toJSONString(
                            book.getAsk().stream().map(pl -> List.of(pl.getPrice(), pl.getQuantity())).collect(Collectors.toList())
                    );

                    OrderBookSnapshotEntity entity = OrderBookSnapshotEntity.builder()
                            .symbol(symbol)
                            .exchange(exchange.name())
                            .bids(bidsJson)
                            .asks(asksJson)
                            .bidCount(book.getBid().size())
                            .askCount(book.getAsk().size())
                            .snapshotTime(System.currentTimeMillis())
                            .createdAt(System.currentTimeMillis())
                            .build();
                    orderBookSnapshotMapper.insert(entity);
                }
            }
        } catch (Exception e) {
            log.warn("[Persistence] Failed to capture order book snapshot: {}", e.getMessage());
        }
    }
}

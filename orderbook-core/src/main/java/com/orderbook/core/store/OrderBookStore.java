package com.orderbook.core.store;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Maps;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.config.ExchangeConnectConfig;
import com.orderbook.core.domain.ExchangeInfo;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.exchange.common.AbstractSymbolOrderBooks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class OrderBookStore {

    private final Map<String, AbstractSymbolOrderBooks> orderBookMap = Maps.newConcurrentMap();

    @Autowired
    private SymbolStore symbolStore;

    @Autowired
    private ExchangeConnectConfig exchangeConnectConfig;

    public String computeKey(String exchangeName, String symbol) {
        return exchangeName + ":" + symbol;
    }

    public String computeKey(ExchangeCode exchangeCode, String symbol) {
        return computeKey(exchangeCode.name(), symbol);
    }

    public OrderBook get(ExchangeCode exchangeCode, String symbol) {
        return get(exchangeCode.name(), symbol);
    }

    public OrderBook get(ExchangeCode exchangeCode, SymbolBo symbol) {
        return get(exchangeCode.name(), symbol.getSymbolId());
    }

    public OrderBook get(String exchangeCode, String symbol) {
        AbstractSymbolOrderBooks abstractSymbolOrderBooks = orderBookMap.get(computeKey(exchangeCode, symbol));
        if (Objects.isNull(abstractSymbolOrderBooks)) {
            return null;
        }
        return abstractSymbolOrderBooks.get();
    }

    /**
     * Check if order book for given exchange/symbol is in stale state
     * (checksum validation failed, rebuild in progress).
     */
    public boolean isStale(ExchangeCode exchange, String symbol) {
        AbstractSymbolOrderBooks books = orderBookMap.get(computeKey(exchange.name(), symbol));
        return books != null && books.isStale();
    }

    public boolean isStale(String exchange, String symbol) {
        AbstractSymbolOrderBooks books = orderBookMap.get(computeKey(exchange, symbol));
        return books != null && books.isStale();
    }

    /** Force-mark a symbol as stale (useful during reconnection). */
    public void markStale(ExchangeCode exchange, String symbol) {
        AbstractSymbolOrderBooks books = orderBookMap.get(computeKey(exchange.name(), symbol));
        if (books != null) {
            books.markStale();
        }
    }

    public Collection<AbstractSymbolOrderBooks> getAllOrderBook() {
        return orderBookMap.values();
    }

    public Collection<AbstractSymbolOrderBooks> getHandler() {
        return orderBookMap.values();
    }

    @PostConstruct
    public void init() {
        Map<String, ExchangeInfo> exchangeInfoMap = exchangeConnectConfig.getExchange();
        List<SymbolBo> activeSymbols = symbolStore.getActiveSymbols();
        if (CollUtil.isEmpty(activeSymbols) || exchangeInfoMap.isEmpty()) {
            log.error("config support platform config is not found, price processing cannot be carried out");
            return;
        }

        for (Map.Entry<String, ExchangeInfo> exchangeEntry : exchangeInfoMap.entrySet()) {
            String exchangeKey = exchangeEntry.getKey();
            ExchangeCode exchangeCode = ExchangeCode.valueOf(exchangeKey.toUpperCase());

            for (SymbolBo symbolBo : activeSymbols) {
                String symbol = symbolBo.getSymbolId();
                String mapKey = computeKey(exchangeKey, symbol);

                orderBookMap.computeIfAbsent(mapKey, k -> new AbstractSymbolOrderBooks() {
                    @Override
                    public String symbol() {
                        return symbol;
                    }

                    @Override
                    public ExchangeCode platform() {
                        return exchangeCode;
                    }
                });
            }
        }
    }
}
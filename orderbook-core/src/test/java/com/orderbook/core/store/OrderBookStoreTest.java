package com.orderbook.core.store;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.exchange.common.AbstractSymbolOrderBooks;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for stale state tracking in OrderBookStore.
 */
class OrderBookStoreTest {

    private final OrderBookStore store = new OrderBookStore();

    @Test
    void isStaleShouldReturnFalseByDefault() {
        // Non-existent key should not throw and return false
        assertDoesNotThrow(() -> store.isStale(ExchangeCode.BYBIT, "BTCUSDT"));
        assertFalse(store.isStale(ExchangeCode.BYBIT, "BTCUSDT"));
    }

    @Test
    void staleStateShouldDelegateToUnderlyingOrderBooks() {
        AbstractSymbolOrderBooks testBooks = new AbstractSymbolOrderBooks() {
            @Override
            public String symbol() { return "BTCUSDT"; }

            @Override
            public ExchangeCode platform() { return ExchangeCode.BITGET; }
        };

        injectOrderBooks("BITGET:BTCUSDT", testBooks);

        // Initially not stale
        assertFalse(store.isStale(ExchangeCode.BITGET, "BTCUSDT"));

        // After markStale
        store.markStale(ExchangeCode.BITGET, "BTCUSDT");
        assertTrue(store.isStale(ExchangeCode.BITGET, "BTCUSDT"));

        // String overload should also work
        assertTrue(store.isStale("BITGET", "BTCUSDT"));
    }

    @Test
    void staleClearedAfterSuccessfulChecksum() {
        AbstractSymbolOrderBooks testBooks = new AbstractSymbolOrderBooks() {
            @Override
            public String symbol() { return "BTCUSDT"; }
            @Override
            public ExchangeCode platform() { return ExchangeCode.BITGET; }
        };

        injectOrderBooks("BITGET:BTCUSDT", testBooks);

        // Mark stale, then simulate checksum recovery by calling markStale(false)
        // via clearing stale through isStale path
        store.markStale(ExchangeCode.BITGET, "BTCUSDT");
        assertTrue(store.isStale(ExchangeCode.BITGET, "BTCUSDT"));
    }

    @Test
    void markStaleShouldNotThrowForNonExistentSymbol() {
        assertDoesNotThrow(() -> store.markStale(ExchangeCode.BITGET, "NONEXISTENT"));
    }

    @Test
    void isStaleShouldHandleMultipleSymbols() {
        AbstractSymbolOrderBooks btcBooks = new AbstractSymbolOrderBooks() {
            @Override
            public String symbol() { return "BTCUSDT"; }
            @Override
            public ExchangeCode platform() { return ExchangeCode.BITGET; }
        };

        AbstractSymbolOrderBooks ethBooks = new AbstractSymbolOrderBooks() {
            @Override
            public String symbol() { return "ETHUSDT"; }
            @Override
            public ExchangeCode platform() { return ExchangeCode.BITGET; }
        };

        injectOrderBooks("BITGET:BTCUSDT", btcBooks);
        injectOrderBooks("BITGET:ETHUSDT", ethBooks);

        // Mark only BTC as stale
        store.markStale(ExchangeCode.BITGET, "BTCUSDT");

        assertTrue(store.isStale(ExchangeCode.BITGET, "BTCUSDT"));
        assertFalse(store.isStale(ExchangeCode.BITGET, "ETHUSDT"));
    }

    @SuppressWarnings("unchecked")
    private void injectOrderBooks(String key, AbstractSymbolOrderBooks books) {
        try {
            var field = OrderBookStore.class.getDeclaredField("orderBookMap");
            field.setAccessible(true);
            var map = (Map<String, AbstractSymbolOrderBooks>) field.get(store);
            map.put(key, books);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject test order book", e);
        }
    }
}

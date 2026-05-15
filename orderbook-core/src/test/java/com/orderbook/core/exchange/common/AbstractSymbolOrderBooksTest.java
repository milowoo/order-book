package com.orderbook.core.exchange.common;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.PriceLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AbstractSymbolOrderBooks stale state tracking.
 */
class AbstractSymbolOrderBooksTest {

    private final AbstractSymbolOrderBooks books = new AbstractSymbolOrderBooks() {
        @Override
        public String symbol() { return "BTCUSDT"; }

        @Override
        public ExchangeCode platform() { return ExchangeCode.BITGET; }
    };

    @Test
    void staleFlagShouldBeFalseByDefault() {
        assertFalse(books.isStale());
    }

    @Test
    void markStaleShouldSetAndKeepFlag() {
        books.markStale();
        assertTrue(books.isStale());
    }

    @Test
    void getShouldReturnOrderBookWithCorrectExchangeAndSymbol() {
        OrderBook result = books.get();
        assertNotNull(result);
        assertEquals(ExchangeCode.BITGET, result.getExchange());
        assertEquals("BTCUSDT", result.getSymbol());
    }

    @Test
    void getShouldReturnOrderBookWhenNoData() {
        OrderBook result = books.get();
        assertNotNull(result);
        // bid/ask may be null when empty (depends on builder behavior)
        assertTrue(result.getBid() == null || result.getBid().isEmpty());
        assertTrue(result.getAsk() == null || result.getAsk().isEmpty());
    }

    @Test
    void checkSumOnEmptyBookShouldReturnFalse() {
        assertFalse(books.checkSum(0, 25));
    }

    @Test
    void insertWithChecksumZeroShouldSkipValidationAndNotSetStale() {
        // Feed data with checksum=0 → validation skipped, no SpringUtil call
        List<PriceLevel> bids = new ArrayList<>();
        bids.add(new PriceLevel(new BigDecimal("50000"), BigDecimal.ONE));

        List<PriceLevel> asks = new ArrayList<>();
        asks.add(new PriceLevel(new BigDecimal("50010"), BigDecimal.ONE));

        OrderBook event = OrderBook.builder()
                .exchange(ExchangeCode.BITGET)
                .symbol("BTCUSDT")
                .bid(bids)
                .ask(asks)
                .checksum(0)
                .build();

        // Should succeed without Spring context (checksum=0 skips validation)
        assertDoesNotThrow(() -> books.onEvent(event, 0, true));
        // Stale should remain false (no validation triggered)
        assertFalse(books.isStale());
    }
}

package com.orderbook.core.backtest;

import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CsvSnapshotLoader — verifies both CSV formats,
 * edge cases, and error handling.
 */
class CsvSnapshotLoaderTest {

    private final CsvSnapshotLoader loader = new CsvSnapshotLoader();

    @Test
    void loadFullBookCsv() throws IOException {
        String path = "src/test/resources/backtest/snapshots_full.csv";
        List<BacktestSnapshot> snapshots = loader.loadCsv(path);

        assertNotNull(snapshots);
        assertEquals(3, snapshots.size());

        BacktestSnapshot first = snapshots.get(0);
        assertEquals(1700000000000L, first.getTime());
        // midPrice = (bestBid + bestAsk) / 2 = (49990 + 50010) / 2 = 50000
        assertEquals(0, new BigDecimal("50000.0").compareTo(first.getMidPrice()));
        assertNotNull(first.getBids());
        assertNotNull(first.getAsks());
        assertEquals(2, first.getBids().size());
        assertEquals(2, first.getAsks().size());

        // Verify bid levels
        assertEquals(0, new BigDecimal("49990.0").compareTo(first.getBids().get(0).getPrice()));
        assertEquals(0, new BigDecimal("1.5").compareTo(first.getBids().get(0).getQuantity()));

        // Verify ask levels
        assertEquals(0, new BigDecimal("50010.0").compareTo(first.getAsks().get(0).getPrice()));
        assertEquals(0, new BigDecimal("1.0").compareTo(first.getAsks().get(0).getQuantity()));
    }

    @Test
    void loadPriceOnlyCsv() throws IOException {
        String path = "src/test/resources/backtest/snapshots_price_only.csv";
        List<BacktestSnapshot> snapshots = loader.loadCsv(path);

        assertNotNull(snapshots);
        assertEquals(3, snapshots.size());

        BacktestSnapshot first = snapshots.get(0);
        assertEquals(1700000000000L, first.getTime());
        assertEquals(0, new BigDecimal("50005.0").compareTo(first.getMidPrice()));

        // Price-only mode: bestBid == bestAsk == midPrice
        assertNotNull(first.getBestBid());
        assertNotNull(first.getBestAsk());
        assertEquals(0, first.getBestBid().compareTo(first.getBestAsk()));
        assertEquals(0, first.getBestBid().compareTo(first.getMidPrice()));
    }

    @Test
    void loadCsvFromInputStream() throws IOException {
        try (InputStream is = new FileInputStream("src/test/resources/backtest/snapshots_full.csv")) {
            List<BacktestSnapshot> snapshots = loader.loadCsv(is);
            assertNotNull(snapshots);
            assertEquals(3, snapshots.size());
        }
    }

    @Test
    void loadCsvWithNonexistentFile() {
        assertThrows(IOException.class, () -> loader.loadCsv("nonexistent.csv"));
    }
}

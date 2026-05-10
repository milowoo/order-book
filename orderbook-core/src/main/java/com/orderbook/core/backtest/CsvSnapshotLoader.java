package com.orderbook.core.backtest;

import com.orderbook.core.domain.PriceLevel;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads historical order book snapshots from CSV files.
 * Supports two formats:
 *
 * <p>Full order book: {@code timestamp_ms,best_bid,best_ask,bid_price_0,bid_qty_0,...,ask_price_0,ask_qty_0,...}
 *
 * <p>Price-only: {@code timestamp_ms,mid_price}
 */
@Slf4j
public class CsvSnapshotLoader {

    /**
     * Load snapshots from a file path.
     */
    public List<BacktestSnapshot> loadCsv(String filePath) throws IOException {
        log.info("[CsvSnapshotLoader] Loading from file: {}", filePath);
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            return parseLines(reader);
        }
    }

    /**
     * Load snapshots from an input stream (for HTTP upload).
     */
    public List<BacktestSnapshot> loadCsv(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return parseLines(reader);
        }
    }

    private List<BacktestSnapshot> parseLines(BufferedReader reader) throws IOException {
        String header = reader.readLine();
        if (header == null || header.isBlank()) {
            log.warn("[CsvSnapshotLoader] Empty CSV file");
            return List.of();
        }

        String[] columns = header.trim().toLowerCase().split(",");
        boolean priceOnly = columns.length >= 2 && "mid_price".equals(columns[1].trim());

        List<BacktestSnapshot> snapshots = new ArrayList<>();
        String line;
        int lineNum = 1;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                String[] parts = line.split(",");
                long time = Long.parseLong(parts[0].trim());

                BacktestSnapshot snapshot = priceOnly
                        ? parsePriceOnlyLine(parts, time)
                        : parseFullBookLine(parts, time);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            } catch (Exception e) {
                log.debug("[CsvSnapshotLoader] Skipping line {}: {}", lineNum, e.getMessage());
            }
        }

        log.info("[CsvSnapshotLoader] Loaded {} snapshots", snapshots.size());
        return snapshots;
    }

    private BacktestSnapshot parsePriceOnlyLine(String[] parts, long time) {
        if (parts.length < 2) return null;
        BigDecimal midPrice = new BigDecimal(parts[1].trim());
        // Create a snapshot where best bid/ask are derived from mid price
        List<PriceLevel> bids = new ArrayList<>();
        List<PriceLevel> asks = new ArrayList<>();
        // For price-only mode, create artificial bid/ask levels around mid
        return new BacktestSnapshot(time, midPrice, midPrice, bids, asks, null);
    }

    private BacktestSnapshot parseFullBookLine(String[] parts, long time) {
        if (parts.length < 5) return null;

        BigDecimal bestBid = new BigDecimal(parts[1].trim());
        BigDecimal bestAsk = new BigDecimal(parts[2].trim());

        List<PriceLevel> bids = new ArrayList<>();
        List<PriceLevel> asks = new ArrayList<>();

        // Find the split between bid and ask levels
        // After timestamp(0), bestBid(1), bestAsk(2):
        // Format: bid_price_0,bid_qty_0,bid_price_1,bid_qty_1,...,ask_price_0,ask_qty_0,...
        // Auto-detect: the ask levels start when price >= bestAsk
        boolean inAsk = false;
        for (int i = 3; i < parts.length - 1; i += 2) {
            try {
                BigDecimal price = new BigDecimal(parts[i].trim());
                BigDecimal qty = new BigDecimal(parts[i + 1].trim());
                if (!inAsk && price.compareTo(bestAsk) >= 0) {
                    inAsk = true;
                }
                if (inAsk) {
                    asks.add(new PriceLevel(price, qty));
                } else {
                    bids.add(new PriceLevel(price, qty));
                }
            } catch (Exception e) {
                // Skip malformed pairs
                break;
            }
        }

        return new BacktestSnapshot(time, bestBid, bestAsk, bids, asks, null);
    }
}

package com.orderbook.core.sor;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.service.FeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains real-time statistics for all exchanges.
 * Refreshed periodically from FeeService and other sources.
 */
@Slf4j
@Service
public class RoutingTable {

    private final FeeService feeService;
    private final Map<ExchangeCode, ExchangeStats> statsMap = new ConcurrentHashMap<>();
    private final Map<ExchangeCode, LatencyTracker> latencyTrackers = new ConcurrentHashMap<>();

    public RoutingTable(FeeService feeService) {
        this.feeService = feeService;
        for (ExchangeCode code : ExchangeCode.values()) {
            statsMap.put(code, ExchangeStats.builder()
                    .exchange(code)
                    .avgLatencyMicros(0)
                    .takerFeeRate(BigDecimal.valueOf(0.001))
                    .makerFeeRate(BigDecimal.valueOf(0.001))
                    .currentSpreadBps(0)
                    .fillProbabilityProxy(1.0)
                    .healthy(true)
                    .lastUpdated(System.currentTimeMillis())
                    .build());
            latencyTrackers.put(code, new LatencyTracker());
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void refreshAll() {
        for (ExchangeCode code : ExchangeCode.values()) {
            ExchangeStats stats = statsMap.get(code);
            if (stats == null) continue;

            stats.setTakerFeeRate(feeService.getTakerRate(code, null));
            stats.setMakerFeeRate(feeService.getMakerRate(code, null));

            LatencyTracker tracker = latencyTrackers.get(code);
            if (tracker != null) {
                stats.setAvgLatencyMicros(tracker.getAverage());
            }

            stats.setLastUpdated(System.currentTimeMillis());
        }
    }

    public ExchangeStats getStats(ExchangeCode exchange) {
        return statsMap.get(exchange);
    }

    public void updateHealth(ExchangeCode exchange, boolean healthy) {
        ExchangeStats stats = statsMap.get(exchange);
        if (stats != null) {
            stats.setHealthy(healthy);
        }
    }

    public void recordLatency(ExchangeCode exchange, long latencyMicros) {
        LatencyTracker tracker = latencyTrackers.get(exchange);
        if (tracker != null) {
            tracker.addSample(latencyMicros);
        }
    }

    public List<ExchangeStats> getAllStats() {
        return new ArrayList<>(statsMap.values());
    }

    public List<ExchangeCode> getHealthyExchanges() {
        List<ExchangeCode> healthy = new ArrayList<>();
        for (Map.Entry<ExchangeCode, ExchangeStats> entry : statsMap.entrySet()) {
            if (entry.getValue().isHealthy()) {
                healthy.add(entry.getKey());
            }
        }
        return healthy;
    }

    /**
     * Exponential moving average latency tracker.
     */
    private static class LatencyTracker {
        private static final double ALPHA = 0.3;
        private double avg = 0;
        private boolean hasValue = false;

        synchronized void addSample(long micros) {
            if (!hasValue) {
                avg = micros;
                hasValue = true;
            } else {
                avg = ALPHA * micros + (1 - ALPHA) * avg;
            }
        }

        synchronized double getAverage() {
            return avg;
        }
    }
}

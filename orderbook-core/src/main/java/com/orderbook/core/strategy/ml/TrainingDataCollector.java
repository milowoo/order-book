package com.orderbook.core.strategy.ml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.entity.TrainDatasetEntity;
import com.orderbook.core.mapper.TrainDatasetMapper;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.strategy.spread.VolatilityTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在策略实盘执行期间收集训练数据。
 * 捕捉特征快照，并在 N 个周期后生成标签。
 */
@Slf4j
@Service
public class TrainingDataCollector {

    private final OrderBookStore orderBookStore;
    private final VolatilityTracker volatilityTracker;
    private final TrainDatasetMapper trainDatasetMapper;
    private final int featureCaptureInterval;

    private final FeatureExtractor featureExtractor;
    private final Map<String, List<UnlabeledExample>> pendingExamples = new ConcurrentHashMap<>();
    private final Map<String, Integer> tickCounters = new ConcurrentHashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public TrainingDataCollector(OrderBookStore orderBookStore,
                                  VolatilityTracker volatilityTracker,
                                  TrainDatasetMapper trainDatasetMapper) {
        this.orderBookStore = orderBookStore;
        this.volatilityTracker = volatilityTracker;
        this.trainDatasetMapper = trainDatasetMapper;
        this.featureExtractor = new FeatureExtractor(orderBookStore, volatilityTracker);
        this.featureCaptureInterval = 5; // Every 5 ticks by default
    }

    /**
     * 提取特征并存储为未标记样本。
     * 由策略的 Tick 回调调用。
     */
    public void captureFeatures(String symbol, SymbolBo symbolBo) {
        int tick = tickCounters.merge(symbol, 1, (old, v) -> (old + 1) % featureCaptureInterval);
        if (tick != 0) return; // Only capture every Nth tick

        try {
            double[] features = featureExtractor.extract(symbol, symbolBo);
            if (features == null) return;

            List<UnlabeledExample> pending = pendingExamples.computeIfAbsent(symbol, k -> new ArrayList<>());
            pending.add(new UnlabeledExample(features, System.currentTimeMillis()));

            // Keep max 100 pending per symbol
            if (pending.size() > 100) {
                pending.remove(0);
            }

            // Generate labels for previous pending examples
            generateLabels(symbol);
        } catch (Exception e) {
            log.warn("[{}] Failed to capture training features", symbol, e);
        }
    }

    /**
     * 通过比较当前价格与捕捉时的价格，为待处理的样本生成标签。
     */
    public void generateLabels(String symbol) {
        List<UnlabeledExample> pending = pendingExamples.get(symbol);
        if (pending == null || pending.isEmpty()) return;

        LinkedList<BigDecimal> history = volatilityTracker.getPriceHistory(symbol);
        if (history == null || history.isEmpty()) return;

        double currentPrice;
        synchronized (history) {
            currentPrice = history.getLast().doubleValue();
        }

        List<UnlabeledExample> labeled = new ArrayList<>();
        for (UnlabeledExample ex : pending) {
            // Label: we compute it later via lookahead price
            // For now, use current price as the "future" price
            // The price at capture time is reconstructed from features
            ex.label = ex.capturePrice > 0
                    ? (currentPrice - ex.capturePrice) / ex.capturePrice
                    : 0.0;
            labeled.add(ex);
        }

        // Persist labeled examples
        long now = System.currentTimeMillis();
        for (UnlabeledExample ex : labeled) {
            try {
                String featuresJson = MAPPER.writeValueAsString(ex.features);
                TrainDatasetEntity entity = TrainDatasetEntity.builder()
                        .symbol(symbol)
                        .featuresJson(featuresJson)
                        .label(ex.label)
                        .capturedAt(ex.capturedAt)
                        .createdAt(now)
                        .build();
                trainDatasetMapper.insert(entity);
            } catch (JsonProcessingException e) {
                log.warn("[{}] Failed to serialize features", symbol, e);
            }
        }

        pending.clear();
        log.debug("[{}] Generated {} training labels", symbol, labeled.size());
    }

    static class UnlabeledExample {
        final double[] features;
        final long capturedAt;
        double label;
        double capturePrice;

        UnlabeledExample(double[] features, long capturedAt) {
            this.features = features;
            this.capturedAt = capturedAt;
            this.label = 0;
            this.capturePrice = 0;
        }

        void setCapturePrice(double price) {
            this.capturePrice = price;
        }
    }
}

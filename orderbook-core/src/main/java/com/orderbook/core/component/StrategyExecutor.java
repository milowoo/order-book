package com.orderbook.core.component;

import com.ctrip.framework.apollo.ConfigService;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.orderbook.core.config.ApolloConfig;
import com.orderbook.core.config.StrategyProps;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.utils.JsonConverter;
import com.orderbook.core.utils.TrackingUtils;
import com.orderbook.core.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyExecutor {

    private final StrategyProps strategyProps;
    private final List<Strategy> strategies;
    private final ApolloConfig apolloConfig;

    private final Map<String, ExecutorService> symbolExecutors = Maps.newConcurrentMap();
    private final Map<String, Map<String, String>> symbolContexts = new ConcurrentHashMap<>();
    private final Map<String, TriggerState> triggerStates = new ConcurrentHashMap<>();

    public void start() {
        try {
            startBotStrategy();
        } catch (Throwable ex) {
            log.error("StrategyExecutor start has exception:", ex);
        }
    }

    private void startBotStrategy() {
        for (String symbol : strategyProps.allSymbols()) {
            if (symbolExecutors.containsKey(symbol)) {
                continue;
            }
            log.info("======>Exec startBotStrategy for symbol {} ", symbol);
            ExecutorService executor = symbolExecutors.computeIfAbsent(symbol,
                    s -> Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                            .setNameFormat(String.format("strategy-robot-%s", symbol))
                            .build()));

            Map<String, String> symbolContext = symbolContexts.computeIfAbsent(symbol, t -> new LinkedHashMap<>());
            SymbolBo symbolBo = strategyProps.findSymbol(symbol);

            // Submit initial one-shot execution; subsequent executions are
            // triggered by PriceMovementDetector (event) or fallbackTrigger (timer).
            executor.submit(new SymbolRunner(symbol, symbolBo, symbolContext, strategies));
        }
    }

    /**
     * Called from PriceMovementDetector (Disruptor thread) or from
     * fallbackTrigger (Spring @Scheduled thread).
     * Non-blocking: submits a one-shot task to the per-symbol executor.
     */
    public void triggerNow(String symbol) {
        TriggerState state = triggerStates.computeIfAbsent(symbol,
                s -> new TriggerState());

        // Throttle: skip if last execution was too recent
        long now = System.currentTimeMillis();
        long minInterval = apolloConfig.getStrategyTriggerMinIntervalMs();
        long lastTime = state.lastTriggerTimeMs.get();
        if ((now - lastTime) < minInterval) {
            return;
        }

        // Duplicate prevention: only one queued trigger per symbol at a time
        if (!state.triggerQueued.compareAndSet(false, true)) {
            return;
        }

        ExecutorService executor = symbolExecutors.get(symbol);
        if (executor == null || executor.isShutdown()) {
            state.triggerQueued.set(false);
            return;
        }

        SymbolBo symbolBo = strategyProps.findSymbol(symbol);
        Map<String, String> ctx = symbolContexts.get(symbol);
        if (symbolBo == null || ctx == null) {
            state.triggerQueued.set(false);
            return;
        }

        executor.submit(() -> {
            try {
                state.lastTriggerTimeMs.set(System.currentTimeMillis());
                new SymbolRunner(symbol, symbolBo, ctx, strategies).run();
            } finally {
                state.triggerQueued.set(false);
            }
        });
    }

    /**
     * Fallback safety net: if a symbol has not been triggered within
     * fallbackIntervalMs, submit a trigger. This ensures strategies
     * continue to run even when price does not move.
     */
    @Scheduled(fixedDelayString = "${strategy.fallback.interval.ms:5000}")
    public void fallbackTrigger() {
        long now = System.currentTimeMillis();
        long fallbackInterval = apolloConfig.getStrategyFallbackIntervalMs();

        for (String symbol : strategyProps.allSymbols()) {
            TriggerState state = triggerStates.get(symbol);
            long lastTime = (state != null)
                    ? state.lastTriggerTimeMs.get()
                    : 0;
            if ((now - lastTime) >= fallbackInterval) {
                triggerNow(symbol);
            }
        }
    }

    @RequiredArgsConstructor
    public static class SymbolRunner implements Runnable {

        private final String symbol;
        private final SymbolBo symbolBo;
        private final Map<String, String> symbolContext;
        private final List<Strategy> strategies;

        @Override
        public void run() {
            boolean globalSwitch = ConfigService.getAppConfig().getBooleanProperty("global.place.switch", true);
            boolean spotLogSwitch = ConfigService.getAppConfig().getBooleanProperty(symbol + ".switch", false);
            try {
                if (spotLogSwitch) {
                    log.info("======>Exec strategy for symbol {} params:{} ", symbol, JsonConverter.toJsonString(symbolBo));
                }
                if (globalSwitch && symbolBo.isOpen()) {
                    String uuid = UUID.randomUUID().toString();
                    TrackingUtils.saveTraceId("id" + uuid);
                    StopWatch stopWatch = new StopWatch();
                    stopWatch.start();
                    executeStrategies();
                    stopWatch.stop();
                    if (spotLogSwitch) {
                        log.info("======>End spot symbol:{} strategy. expand:{} ms.", symbol, stopWatch.getTime());
                    }
                } else {
                    if (spotLogSwitch) {
                        log.error("symbol:{} strategy can not start, globalSwitch:{}, symbolOpen:{}",
                                symbol, globalSwitch, symbolBo.isOpen());
                    }
                }
                TrackingUtils.clearTraceId();
            } catch (Exception ex) {
                log.error(String.format("Run %s spot strategy has error", symbol), ex);
            }
        }

        private void executeStrategies() {
            for (Strategy strategy : strategies) {
                try {
                    long start = System.currentTimeMillis();
                    strategy.execute(symbol, symbolContext);
                    long cost = System.currentTimeMillis() - start;
                    if (cost > 1000) {
                        log.warn("[{}] Strategy '{}' took {}ms", symbol, strategy.getName(), cost);
                    }
                } catch (Exception ex) {
                    log.error("[{}] Strategy '{}' execution error", symbol, strategy.getName(), ex);
                }
            }
        }
    }

    private static class TriggerState {
        private final AtomicLong lastTriggerTimeMs = new AtomicLong(0);
        private final AtomicBoolean triggerQueued = new AtomicBoolean(false);
    }
}

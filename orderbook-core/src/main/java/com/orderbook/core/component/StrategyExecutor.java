package com.orderbook.core.component;

import com.ctrip.framework.apollo.ConfigService;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.orderbook.core.config.StrategyProps;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.utils.JsonConverter;
import com.orderbook.core.utils.TrackingUtils;
import com.orderbook.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyExecutor {

    private final StrategyProps strategyProps;
    private final List<Strategy> strategies;

    private final Map<String, ScheduledExecutorService> symbolExecutors = Maps.newConcurrentMap();
    private final Map<String, Map<String, String>> symbolContexts = new ConcurrentHashMap<>();

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
            ScheduledExecutorService executorService = symbolExecutors.computeIfAbsent(symbol,
                    s -> Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder()
                            .setNameFormat(String.format("strategy-robot-%s", symbol))
                            .build()));

            Map<String, String> symbolContext = symbolContexts.computeIfAbsent(symbol, t -> new LinkedHashMap<>());
            SymbolBo symbolBo = strategyProps.findSymbol(symbol);
            executorService.scheduleAtFixedRate(new SymbolRunner(symbol, symbolBo, symbolContext, strategies),
                    0, symbolBo.getUpdateIntervalMs(), TimeUnit.MILLISECONDS);
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
}

package com.orderbook.core.monitor;

import com.orderbook.core.exchange.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom HealthIndicator that reports WebSocket connection health
 * for all configured exchange handlers.
 * 自定义健康检查指示器，用于汇报所有已配置的交易所处理器的 WebSocket 连接健康状况。
 */
@Slf4j
@Component
public class ExchangeHealthIndicator implements HealthIndicator {

    private final BybitHandler bybitHandler;
    private final BinanceHandler binanceHandler;
    private final BitgetHandler bitgetHandler;
    private final GlobalHandler globalHandler;

    public ExchangeHealthIndicator(BybitHandler bybitHandler,
                                   BinanceHandler binanceHandler,
                                   BitgetHandler bitgetHandler,
                                   GlobalHandler globalHandler) {
        this.bybitHandler = bybitHandler;
        this.binanceHandler = binanceHandler;
        this.bitgetHandler = bitgetHandler;
        this.globalHandler = globalHandler;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        boolean bybitHealthy = isHealthy("Bybit", bybitHandler.isConnectionHealthy(), details);
        boolean binanceHealthy = isHealthy("Binance", binanceHandler.isConnectionHealthy(), details);
        boolean bitgetHealthy = isHealthy("Bitget", bitgetHandler.isConnectionHealthy(), details);
        boolean globalHealthy = isHealthy("Global", globalHandler.isConnectionHealthy(), details);

        boolean anyHealthy = bybitHealthy || binanceHealthy || bitgetHealthy || globalHealthy;

        if (anyHealthy) {
            return Health.up().withDetails(details).build();
        }
        return Health.down().withDetails(details).build();
    }

    private boolean isHealthy(String name, boolean healthy, Map<String, Object> details) {
        details.put(name, healthy ? "UP" : "DOWN");
        return healthy;
    }
}

package com.orderbook.core.exchange.handler;

import cn.hutool.core.collection.CollUtil;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.interfaces.StreamConnectorFactory;
import com.orderbook.connector.stream.global.GlobalStreamingExchange;
import com.orderbook.core.config.ExchangeConnectConfig;
import com.orderbook.core.domain.ExchangeInfo;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.SymbolStore;
import io.reactivex.rxjava3.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handler for the OSL_GLOBAL exchange connection.
 * Manages public streaming connection with auto-reconnect and heartbeat.
 */
@Slf4j
@Component
public class GlobalHandler {

    private final StreamConnectorFactory streamConnectorFactory;
    private final ExchangeConnectConfig exchangeConnectConfig;
    private final SymbolStore symbolStore;

    private GlobalStreamingExchange pubExchange;
    private final List<Disposable> disposables = new ArrayList<>();

    // Heartbeat tracking
    private final AtomicLong lastMessageTime = new AtomicLong(System.currentTimeMillis());
    private static final long HEARTBEAT_TIMEOUT_MS = 60_000L;

    public GlobalHandler(@Qualifier("streamConnectorFactory") StreamConnectorFactory streamConnectorFactory,
                         ExchangeConnectConfig exchangeConnectConfig,
                         SymbolStore symbolStore) {
        this.streamConnectorFactory = streamConnectorFactory;
        this.exchangeConnectConfig = exchangeConnectConfig;
        this.symbolStore = symbolStore;
    }

    private static ExchangeCode getExchange() {
        return ExchangeCode.OSL_GLOBAL;
    }

    @PostConstruct
    public void init() {
        connectAndSubscribe();
    }

    void connectAndSubscribe() {
        List<SymbolBo> symbolBos = symbolStore.getActiveSymbols();
        if (CollUtil.isEmpty(symbolBos)) {
            log.warn("[GlobalHandler] No active symbols, skipping connection");
            return;
        }

        ExchangeInfo exchangeInfo = exchangeConnectConfig.getExchangeInfo(getExchange().name());
        if (exchangeInfo == null) {
            log.warn("[GlobalHandler] No connect configured for {}", getExchange().name());
            return;
        }

        try {
            if (Boolean.TRUE.equals(exchangeInfo.getStreamPublicUse())) {
                pubExchange = (GlobalStreamingExchange) streamConnectorFactory.getExchange(getExchange(), false);
                if (!pubExchange.isAlive()) {
                    pubExchange.connect().blockingAwait();
                }
                log.info("[GlobalHandler] Public connection established");
            }
        } catch (Exception e) {
            log.error("[GlobalHandler] Failed to connect", e);
            throw e;
        }
        lastMessageTime.set(System.currentTimeMillis());
        log.info("[GlobalHandler] connectAndSubscribe complete");
    }

    public void dispose() {
        log.info("[GlobalHandler] Disposing all subscriptions...");
        disposables.forEach(Disposable::dispose);
        disposables.clear();
    }

    @PreDestroy
    public void onExit() {
        dispose();
        if (pubExchange != null) {
            try { pubExchange.disconnect(); } catch (Exception e) { /* ignore */ }
        }
    }

    public void rebuildOrderBook(String symbol) {
        log.info("[GlobalHandler] rebuildOrderBook for symbol: {}", symbol);
    }

    /** Check connection health and reconnect if needed. */
    @Scheduled(fixedDelay = 30_000)
    public void checkConnection() {
        boolean alive = pubExchange == null || pubExchange.isAlive();
        long idle = System.currentTimeMillis() - lastMessageTime.get();
        if (!alive || idle > HEARTBEAT_TIMEOUT_MS) {
            log.warn("[GlobalHandler] Connection unhealthy: alive={}, idle={}ms", alive, idle);
            reconnect();
        }
    }

    private synchronized void reconnect() {
        log.warn("[GlobalHandler] Reconnecting...");
        dispose();
        if (pubExchange != null) {
            try { pubExchange.disconnect(); } catch (Exception e) { /* ignore */ }
        }
        pubExchange = null;
        connectAndSubscribe();
        lastMessageTime.set(System.currentTimeMillis());
        log.warn("[GlobalHandler] Reconnect complete");
    }

    /**
     * Check if the exchange connection is healthy.
     */
    public boolean isConnectionHealthy() {
        return pubExchange != null && pubExchange.isAlive();
    }
}

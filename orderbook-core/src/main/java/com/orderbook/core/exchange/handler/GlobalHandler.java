package com.orderbook.core.exchange.handler;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.interfaces.StreamConnectorFactory;
import com.orderbook.core.config.ExchangeConnectConfig;
import com.orderbook.core.domain.ExchangeInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalHandler {

    @Qualifier("streamConnectorFactory")
    private final StreamConnectorFactory streamConnectorFactory;
    private final ExchangeConnectConfig exchangeConnectConfig;

    public void rebuildOrderBook(String symbol) {
        log.info("GlobalHandler rebuildOrderBook for symbol: {}", symbol);
    }
}

package com.orderbook.core.exchange.common;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.exchange.handler.BitgetHandler;
import com.orderbook.core.exchange.handler.GlobalHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RebuildOrderBookUtil {
    private final GlobalHandler globalHandler;
    private final BitgetHandler bitgetHandler;

    public void rebuildOrderBook(ExchangeCode exchangeCode, String symbol) {
        switch (exchangeCode) {
            case BITGET:
                bitgetHandler.rebuildOrderBook(symbol);
                break;
            case OSL_GLOBAL:
                globalHandler.rebuildOrderBook(symbol);
                break;
            default:
                break;
        }
    }
}
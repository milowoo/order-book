package com.orderbook.connector.global.service;

import com.orderbook.connector.global.GlobalAdapters;
import com.orderbook.connector.global.GlobalExchange;
import org.knowm.xchange.bitget.dto.marketdata.*;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.instrument.Instrument;

import java.io.IOException;
import java.util.List;

public class GlobalMarketDataServiceRaw extends GlobalBaseService {

    public GlobalMarketDataServiceRaw(GlobalExchange exchange) {
        super(exchange);
    }

    public BitgetServerTime getBitgetServerTime() throws IOException {
        return global.serverTime().getData();
    }

    public List<BitgetCoinDto> getBitgetCoinDtoList(Currency currency) throws IOException {
        return global.coins(GlobalAdapters.toString(currency)).getData();
    }

    public List<BitgetSymbolDto> getBitgetSymbolDtos(Instrument instrument) throws IOException {
        return global.symbols(null).getData();
    }

    public List<BitgetTickerDto> getBitgetTickerDtos(Instrument instrument) throws IOException {
        return global.tickers(GlobalAdapters.toString(instrument)).getData();
    }

    public BitgetMarketDepthDto getBitgetMarketDepthDtos(Instrument instrument) throws IOException {
        return global.orderbook(GlobalAdapters.toString(instrument)).getData();
    }
}
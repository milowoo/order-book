package com.orderbook.connector.bitget.service;

import com.orderbook.connector.bitget.BitgetAdapters;
import com.orderbook.connector.bitget.BitgetExchange;
import org.knowm.xchange.bitget.dto.marketdata.BitgetCoinDto;
import org.knowm.xchange.bitget.dto.marketdata.BitgetMarketDepthDto;
import org.knowm.xchange.bitget.dto.marketdata.BitgetServerTime;
import org.knowm.xchange.bitget.dto.marketdata.BitgetSymbolDto;
import org.knowm.xchange.bitget.dto.marketdata.BitgetTickerDto;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.instrument.Instrument;

import java.io.IOException;
import java.util.List;

public class BitgetMarketDataServiceRaw extends BitgetBaseService {

    public BitgetMarketDataServiceRaw(BitgetExchange exchange) {
        super(exchange);
    }

    public BitgetServerTime getBitgetServerTime() throws IOException {
        return bitget.serverTime().getData();
    }

    public List<BitgetCoinDto> getBitgetCoinDtoList(Currency currency) throws IOException {
        return bitget.coins(BitgetAdapters.toString(currency)).getData();
    }

    public List<BitgetSymbolDto> getBitgetSymbolDtos(Instrument instrument) throws IOException {
        return bitget.symbols(BitgetAdapters.toString(instrument)).getData();
    }

    public List<BitgetTickerDto> getBitgetTickerDtos(Instrument instrument) throws IOException {
        return bitget.tickers(BitgetAdapters.toString(instrument)).getData();
    }

    public BitgetMarketDepthDto getBitgetMarketDepthDtos(Instrument instrument) throws IOException {
        return bitget.orderbook(BitgetAdapters.toString(instrument)).getData();
    }
}
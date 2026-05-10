package com.orderbook.connector.global.service;

import com.orderbook.connector.global.GlobalErrorAdapter;
import com.orderbook.connector.global.GlobalAdapters;
import com.orderbook.connector.global.GlobalExchange;
import org.knowm.xchange.bitget.config.Config;
import org.knowm.xchange.bitget.dto.BitgetException;
import org.knowm.xchange.bitget.dto.marketdata.BitgetCoinDto;
import org.knowm.xchange.bitget.dto.marketdata.BitgetTickerDto;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.ExchangeHealth;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.Params;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GlobalMarketDataService extends GlobalMarketDataServiceRaw implements MarketDataService {

    public GlobalMarketDataService(GlobalExchange exchange) {
        super(exchange);
    }

    public List<Currency> getCurrencies() throws IOException {
        try {
            return getBitgetCoinDtoList(null).stream()
                    .map(BitgetCoinDto::getCurrency)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (BitgetException e) {
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public ExchangeHealth getExchangeHealth() {
        try {
            Instant serverTime = getBitgetServerTime().getServerTime();
            Instant localTime = Instant.now(Config.getInstance().getClock());

            // timestamps shouldn't diverge by more than 10 minutes
            if (Duration.between(serverTime, localTime).toMinutes() < 10) {
                return ExchangeHealth.ONLINE;
            }
        } catch (BitgetException | IOException e) {
            return ExchangeHealth.OFFLINE;
        }
        return ExchangeHealth.OFFLINE;
    }

    @Override
    public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws IOException {
        return getTicker((Instrument) currencyPair, args);
    }

    @Override
    public Ticker getTicker(Instrument instrument, Object... args) throws IOException {
        try {
            List<BitgetTickerDto> tickers = getBitgetTickerDtos(instrument);
            return GlobalAdapters.toTicker(tickers.get(0));
        } catch (BitgetException e) {
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public List<Ticker> getTickers(Params params) throws IOException {
        try {
            return getBitgetTickerDtos(null).stream()
                    .map(GlobalAdapters::toTicker)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (BitgetException e) {
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws IOException {
        return getOrderBook((Instrument) currencyPair, args);
    }

    @Override
    public OrderBook getOrderBook(Instrument instrument, Object... args) throws IOException {
        Objects.requireNonNull(instrument);
        try {
            return GlobalAdapters.toOrderBook(getBitgetMarketDepthDtos(instrument), instrument);
        } catch (BitgetException e) {
            throw GlobalErrorAdapter.adapt(e);
        }
    }
}
package com.orderbook.connector.stream.binance;

import com.orderbook.connector.stream.binance.dto.BinanceRawTrade;
import com.orderbook.connector.stream.binance.dto.DepthBinanceWebSocketTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.knowm.xchange.binance.BinanceAdapters;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.dto.marketdata.*;
import org.knowm.xchange.binance.service.BinanceMarketDataService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.derivative.FuturesContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper.getObjectMapper;
import static java.util.Collections.emptyMap;

public class BinanceStreamingMarketDataService implements StreamingMarketDataService {

    private static final Logger LOG = LoggerFactory.getLogger(BinanceStreamingMarketDataService.class);
    private static final String DELIMITER = "";

    private final BinanceStreamingService service;
    private final String orderBookUpdateFrequencyParameter;
    private final boolean realtimeOrderBookTicker;
    private final int orderBookFetchLimitParameter;

    private final Runnable onApiCall;

    private final Map<Instrument, Observable<OrderBook>> orderbookSubscriptions = new ConcurrentHashMap<>();
    private final Map<Instrument, Observable<List<BinanceRawTrade>>> tradeSubscriptions = new ConcurrentHashMap<>();
    private final Map<Instrument, Observable<BinanceTicker24h>> tickerSubscriptions = new ConcurrentHashMap<>();
    private final Map<Instrument, Observable<BinanceBookTicker>> bookTickerSubscriptions = new ConcurrentHashMap<>();
    private final Map<Instrument, Map<KlineInterval, Observable<BinanceKline>>> klineSubscriptions = new ConcurrentHashMap<>();
    private final Map<Instrument, Observable<BinanceWebSocketTransaction>> orderBookRawUpdatesSubscriptions = new ConcurrentHashMap<>();
    private Map<KlineInterval, Observable<List<BinanceTicker24h>>> allRollingWindowTickerSubscriptions;

    private static final Scheduler bookSnapshotsScheduler =
            Schedulers.from(
                    Executors.newSingleThreadExecutor(
                            new ThreadFactoryBuilder()
                                    .setDaemon(true)
                                    .setNameFormat("binancefuture-book-snapshots-%d")
                                    .build()));
    private final ObjectMapper mapper = getObjectMapper();
    private final BinanceMarketDataService marketDataService;

    private final AtomicBoolean fallback = new AtomicBoolean();
    private final AtomicReference<Runnable> fallbackOnApiCall = new AtomicReference<>(() -> {
    });

    public BinanceStreamingMarketDataService(
            BinanceStreamingService service,
            BinanceMarketDataService marketDataService,
            Runnable onApiCall,
            String orderBookUpdateFrequencyParameter,
            boolean realtimeOrderBookTicker,
            int orderBookFetchLimitParameter) {
        this.service = service;
        this.orderBookUpdateFrequencyParameter = orderBookUpdateFrequencyParameter;
        this.realtimeOrderBookTicker = realtimeOrderBookTicker;
        this.orderBookFetchLimitParameter = orderBookFetchLimitParameter;
        this.marketDataService = marketDataService;
        this.onApiCall = onApiCall;
    }

    public void openSubscriptions(info.bitrich.xchangestream.core.ProductSubscription productSubscription,
                                   KlineSubscription klineSubscription) {
        // Subscriptions are managed lazily via computeIfAbsent in the getter methods
    }

    private Observable<OrderBook> initOrderBookIfAbsent(Instrument instrument) {
        String channelName = getPrefix(instrument) + "@" + BinanceSubscriptionType.DEPTH.getType();
        return service.subscribeChannel(channelName)
                .map(jsonNode -> {
                    try {
                        BinanceOrderbook binanceOrderbook = mapper.treeToValue(jsonNode.get("data"), BinanceOrderbook.class);
                        return convertToOrderBook(binanceOrderbook, instrument);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .share();
    }

    private Observable<BinanceBookTicker> initRawBookTickerSubscription(Instrument instrument) {
        String channelName = getPrefix(instrument) + "@" + BinanceSubscriptionType.BOOK_TICKER.getType();
        return service.subscribeChannel(channelName)
                .map(jsonNode -> {
                    try {
                        return mapper.treeToValue(jsonNode.get("data"), BinanceBookTicker.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .share();
    }

    private Map<KlineInterval, Observable<BinanceKline>> initKlineSubscription(Instrument instrument) {
        Map<KlineInterval, Observable<BinanceKline>> intervalMap = new ConcurrentHashMap<>();
        return intervalMap;
    }

    private static String getPrefix(Instrument pair) {
        String prefix = String.join("", pair.toString().split("/")).toLowerCase();
        if (pair instanceof FuturesContract) {
            prefix = String.join("", ((FuturesContract) pair).getCurrencyPair().toString().split("/")).toLowerCase();
        }
        return prefix;
    }

    private static OrderBook convertToOrderBook(BinanceOrderbook binanceOrderbook, Instrument instrument) {
        List<LimitOrder> asks = new ArrayList<>();
        List<LimitOrder> bids = new ArrayList<>();
        binanceOrderbook.asks.forEach(
                (key, value) ->
                        asks.add(
                                new LimitOrder.Builder(Order.OrderType.ASK, instrument)
                                        .limitPrice(key)
                                        .originalAmount(value)
                                        .build()));
        binanceOrderbook.bids.forEach(
                (key, value) ->
                        bids.add(
                                new LimitOrder.Builder(Order.OrderType.BID, instrument)
                                        .limitPrice(key)
                                        .originalAmount(value)
                                        .build()));
        return new OrderBook(Date.from(Instant.now()), asks, bids);
    }

    @Override
    public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
        if (!service.isLiveSubscriptionEnabled()
                || !service.getProductSubscription().getOrderBook().contains(currencyPair)) {
            throw new ExchangeSecurityException("Up-front subscription required");
        }
        return orderbookSubscriptions.computeIfAbsent(currencyPair, this::initOrderBookIfAbsent);
    }

    @Override
    public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
        if (realtimeOrderBookTicker) {
            return getRawBookTicker(currencyPair)
                    .map(BinanceBookTicker::getTicker)
                    .filter(t -> t.getInstrument().equals(currencyPair));
        }
        return getRawTicker(currencyPair)
                .map(raw -> BinanceAdapters.toTicker(raw, false));
    }

    @Override
    public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
        return getRawTrades(currencyPair)
                .map(BinanceStreamingAdapters::adaptRawTrade);
    }

    @Override
    public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
        if (!service.isLiveSubscriptionEnabled()
                || !service.getProductSubscription().getOrderBook().contains(instrument)) {
            throw new ExchangeSecurityException("Up-front subscription required");
        }
        return orderbookSubscriptions.computeIfAbsent(instrument, this::initOrderBookIfAbsent);
    }

    @Override
    public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
        if (realtimeOrderBookTicker) {
            return getRawBookTicker(instrument)
                    .map(BinanceBookTicker::getTicker)
                    .filter(t -> t.getInstrument().equals(instrument));
        }
        return getRawTicker(instrument)
                .map(raw -> BinanceAdapters.toTicker(raw, instrument instanceof FuturesContract));
    }

    @Override
    public Observable<Trade> getTrades(Instrument instrument, Object... args) {
        return getRawTrades(instrument)
                .map(BinanceStreamingAdapters::adaptRawTrade);
    }

    public Observable<BinanceKline> getKlines(Instrument instrument, KlineInterval interval) {
        if (!service.isLiveSubscriptionEnabled()
                || !service.getKlineSubscription().contains(instrument, interval)) {
            throw new ExchangeSecurityException("Up-front subscription required");
        }
        return klineSubscriptions
                .computeIfAbsent(instrument, this::initKlineSubscription)
                .get(interval);
    }

    public Observable<BinanceBookTicker> getRawBookTicker(Instrument instrument) {
        if (!service.isLiveSubscriptionEnabled()
                || !service.getProductSubscription().getTicker().contains(instrument)) {
            throw new ExchangeSecurityException("Up-front subscription required");
        }
        return bookTickerSubscriptions.computeIfAbsent(instrument, this::initRawBookTickerSubscription);
    }

    public Observable<BinanceRawTrade> getRawTrades(Instrument instrument) {
        return service.getRawTrades(instrument);
    }

    public Observable<BinanceTicker24h> getRawTicker(Instrument instrument) {
        return service.getRawTicker(instrument);
    }

    public Observable<BinanceBookTicker> getRawBookTicker(CurrencyPair currencyPair) {
        return getRawBookTicker((Instrument) currencyPair);
    }

    public Observable<BinanceTicker24h> getRawTicker(CurrencyPair currencyPair) {
        return getRawTicker((Instrument) currencyPair);
    }

    public Observable<BinanceRawTrade> getRawTrades(CurrencyPair currencyPair) {
        return getRawTrades((Instrument) currencyPair);
    }

    public Observable<List<BinanceTicker24h>> rollingWindow(KlineInterval windowSize) {
        if (!service.isLiveSubscriptionEnabled()
                || !service.getProductSubscription().getTicker().stream().anyMatch(instrument -> true)) {
            throw new ExchangeSecurityException("Up-front subscription required");
        }
        if (windowSize.equals(KlineInterval.h1)
                || windowSize.equals(KlineInterval.h4)
                || windowSize.equals(KlineInterval.d1)) {
            return allRollingWindowTickerSubscriptions.computeIfAbsent(
                    windowSize,
                    ws -> {
                        String channelName = "!ticker@" + ws.code();
                        return service.subscribeChannel(channelName)
                                .map(jsonNode -> {
                                    if (jsonNode.has("data")) {
                                        try {
                                            return mapper.treeToValue(
                                                    jsonNode.get("data"),
                                                    mapper.getTypeFactory().constructCollectionType(List.class, BinanceTicker24h.class));
                                        } catch (Exception e) {
                                            return java.util.Collections.<BinanceTicker24h>emptyList();
                                        }
                                    }
                                    return java.util.Collections.<BinanceTicker24h>emptyList();
                                })
                                .share();
                    });
        } else {
            throw new UnsupportedOperationException("RollingWindow not supported for other window size!");
        }
    }

    public Observable<BinanceTicker24h> getRawTicker(CurrencyPair currencyPair, boolean isFuture) {
        return getRawTicker(currencyPair);
    }

    public Observable<BinanceRawTrade> getRawTrades(CurrencyPair currencyPair, boolean isFuture) {
        return getRawTrades(currencyPair);
    }

    public Observable<BinanceBookTicker> getRawBookTicker(CurrencyPair currencyPair, boolean isFuture) {
        return getRawBookTicker(currencyPair);
    }

    public Observable<BinanceKline> getKlines(CurrencyPair currencyPair, KlineInterval interval, boolean isFuture) {
        return getKlines((Instrument) currencyPair, interval);
    }

    public Observable<BinanceTicker24h> getRawTicker(Instrument instrument, boolean isFuture) {
        return getRawTicker(instrument);
    }

    public Observable<BinanceRawTrade> getRawTrades(Instrument instrument, boolean isFuture) {
        return getRawTrades(instrument);
    }

    public Observable<BinanceBookTicker> getRawBookTicker(Instrument instrument, boolean isFuture) {
        return getRawBookTicker(instrument);
    }

    public Observable<OrderBook> getOrderBook(Instrument instrument, boolean isFuture) {
        return getOrderBook(instrument);
    }

    public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, boolean isFuture) {
        return getOrderBook(currencyPair);
    }

}
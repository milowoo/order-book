package com.orderbook.connector.stream.bybit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.orderbook.connector.stream.bybit.dto.marketdata.BybitOrderbook;
import com.orderbook.connector.stream.bybit.dto.marketdata.BybitPublicOrder;
import com.orderbook.connector.stream.bybit.dto.trade.BybitTrade;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.OrderBookUpdate;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.knowm.xchange.bybit.BybitAdapters.convertToBybitSymbol;

@Slf4j
public class BybitStreamingMarketDataService implements StreamingMarketDataService {
    private final Logger LOG = LoggerFactory.getLogger(BybitStreamingMarketDataService.class);
    private final BybitStreamingService streamingService;
    private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

    public static final String TRADE = "publicTrade.";
    public static final String ORDERBOOK = "orderbook.";
    public static final String TICKER = "tickers.";

    private final Map<String, OrderBook> orderBookMap = new HashMap<>();
    private final Map<Instrument, PublishSubject<List<OrderBookUpdate>>> orderBookUpdatesSubscriptions;

    public BybitStreamingMarketDataService(BybitStreamingService streamingService) {
        this.streamingService = streamingService;
        this.orderBookUpdatesSubscriptions = new ConcurrentHashMap<>();
    }

    public Observable<OrderBook> getOrderBookNew(Instrument instrument, Object... args) {
        String depth = "50";
        if (args.length > 0 && args[0] != null) {
            depth = args[0].toString();
        }
        String channelUniqueId = ORDERBOOK + depth + "." + convertToBybitSymbol(instrument);
        return streamingService
                .subscribeChannel(channelUniqueId)
                .map(jsonNode -> {
                    try {
                        BybitOrderbook bybitOrderBooks = mapper.treeToValue(jsonNode, BybitOrderbook.class);
                        String type = bybitOrderBooks.getDataType();
                        LOG.info("bybit type: {} u {} symbol {} ask size {} bid size {}",
                                type,
                                bybitOrderBooks.getData().getU(),
                                bybitOrderBooks.getData().getSymbolName(),
                                bybitOrderBooks.getData().getAsk().size(),
                                bybitOrderBooks.getData().getBid().size()
                        );
                        if (type.equalsIgnoreCase("snapshot")) {
                            return BybitStreamAdapters.adaptOrderBook(bybitOrderBooks, instrument);
                        } else if (type.equalsIgnoreCase("delta")) {
                            return BybitStreamAdapters.adaptOrderBook(bybitOrderBooks, instrument);
                        }
                        return new OrderBook(null, Lists.newArrayList(), Lists.newArrayList());
                    } catch (IllegalStateException e) {
                        LOG.warn("Resubscribing {} channel after adapter error {}", instrument, e.getMessage());
                        // Resubscribe to the channel, triggering a new snapshot
                        if (streamingService.isSocketOpen()) {
                            streamingService.sendMessage(streamingService.getUnsubscribeMessage(channelUniqueId, args));
                            streamingService.sendMessage(streamingService.getSubscribeMessage(channelUniqueId, args));
                        }
                        return new OrderBook(null, Lists.newArrayList(), Lists.newArrayList());
                    }
                })
                .filter(orderBook -> !orderBook.getBids().isEmpty() || !orderBook.getAsks().isEmpty());
    }

    @Override
    public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
        String depth = "50";
        AtomicLong orderBookUpdateIdPrev = new AtomicLong();
        if (args.length > 0 && args[0] != null) {
            depth = args[0].toString();
        }
        String channelUniqueId = ORDERBOOK + depth + "." + convertToBybitSymbol(instrument);
        return streamingService
                .subscribeChannel(channelUniqueId)
                .map(jsonNode -> {
                    try {
                        BybitOrderbook bybitOrderBooks = mapper.treeToValue(jsonNode, BybitOrderbook.class);
                        String type = bybitOrderBooks.getDataType();
                        if (type.equalsIgnoreCase("snapshot")) {
                            OrderBook orderBook = BybitStreamAdapters.adaptOrderBook(bybitOrderBooks, instrument);
                            orderBookUpdateIdPrev.set(bybitOrderBooks.getData().getU());
                            orderBookMap.put(channelUniqueId, orderBook);
                            return orderBook;
                        } else if (type.equalsIgnoreCase("delta")) {
                            return applyDeltaSnapshot(channelUniqueId, instrument, bybitOrderBooks, orderBookUpdateIdPrev);
                        }
                        return new OrderBook(null, Lists.newArrayList(), Lists.newArrayList());
                    } catch (IllegalStateException e) {
                        LOG.warn("Resubscribing {} channel after adapter error {}", instrument, e.getMessage());
                        orderBookMap.remove(channelUniqueId);
                        if (streamingService.isSocketOpen()) {
                            streamingService.sendMessage(streamingService.getUnsubscribeMessage(channelUniqueId, args));
                            streamingService.sendMessage(streamingService.getSubscribeMessage(channelUniqueId, args));
                        }
                        return new OrderBook(null, Lists.newArrayList(), Lists.newArrayList());
                    }
                })
                .filter(orderBook -> !orderBook.getBids().isEmpty() && !orderBook.getAsks().isEmpty());
    }

    private OrderBook applyDeltaSnapshot(
            String channelUniqueId,
            Instrument instrument,
            BybitOrderbook bybitOrderBookUpdate,
            AtomicLong orderBookUpdateIdPrev
    ) {
        OrderBook orderBook = orderBookMap.getOrDefault(channelUniqueId, null);
        if (orderBook == null) {
            LOG.debug("Failed to get orderBook, channelUniqueId={}", channelUniqueId);
            return new OrderBook(null, Lists.newArrayList(), Lists.newArrayList());
        }
        if (orderBookUpdateIdPrev.incrementAndGet() == bybitOrderBookUpdate.getData().getU()) {
            LOG.debug("orderBookUpdate id {}, seq {} ",
                    bybitOrderBookUpdate.getData().getU(),
                    bybitOrderBookUpdate.getData().getSeq());
            List<BybitPublicOrder> asks = bybitOrderBookUpdate.getData().getAsk();
            List<BybitPublicOrder> bids = bybitOrderBookUpdate.getData().getBid();
            Date timestamp = new Date(Long.parseLong(bybitOrderBookUpdate.getTs()));
            asks.forEach(bybitPublicOrder ->
                    orderBook.update(BybitStreamAdapters.adaptOrderBookOrder(bybitPublicOrder, instrument, Order.OrderType.ASK, timestamp))
            );
            bids.forEach(bybitPublicOrder ->
                    orderBook.update(BybitStreamAdapters.adaptOrderBookOrder(bybitPublicOrder, instrument, Order.OrderType.BID, timestamp))
            );
            if (orderBookUpdatesSubscriptions.get(instrument) != null) {
                orderBookUpdatesSubscriptions(instrument, asks, bids, timestamp);
            }
        } else {
            LOG.error("orderBookUpdate channelUniqueId {} id sequence failed, expected {}, actual {}",
                    channelUniqueId, orderBookUpdateIdPrev.get(), bybitOrderBookUpdate.getData().getU());
            throw new IllegalStateException("orderBookUpdate id sequence failed");
        }
        return orderBook;
    }

    @Override
    public Observable<List<OrderBookUpdate>> getOrderBookUpdates(Instrument instrument, Object... args) {
        return orderBookUpdatesSubscriptions.computeIfAbsent(instrument, k -> PublishSubject.create());
    }

    private void orderBookUpdatesSubscriptions(
            Instrument instrument,
            List<BybitPublicOrder> asks,
            List<BybitPublicOrder> bids,
            Date date
    ) {
        List<OrderBookUpdate> orderBookUpdates = new ArrayList<>();
        for (BybitPublicOrder ask : asks) {
            OrderBookUpdate o = new OrderBookUpdate(
                    Order.OrderType.ASK,
                    new BigDecimal(ask.getSize()),
                    instrument,
                    new BigDecimal(ask.getPrice()),
                    date,
                    new BigDecimal(ask.getSize())
            );
            orderBookUpdates.add(o);
        }
        for (BybitPublicOrder bid : bids) {
            OrderBookUpdate o = new OrderBookUpdate(
                    Order.OrderType.BID,
                    new BigDecimal(bid.getSize()),
                    instrument,
                    new BigDecimal(bid.getPrice()),
                    date,
                    new BigDecimal(bid.getSize())
            );
            orderBookUpdates.add(o);
        }
        orderBookUpdatesSubscriptions.get(instrument).onNext(orderBookUpdates);
    }

    @Override
    public Observable<Trade> getTrades(Instrument instrument, Object... args) {
        String channelUniqueId = TRADE + convertToBybitSymbol(instrument);
        return streamingService
                .subscribeChannel(channelUniqueId)
                .filter(message -> message.has("data"))
                .flatMap(jsonNode -> {
                    List<BybitTrade> bybitTradeList = mapper.treeToValue(
                            jsonNode.get("data"),
                            mapper.getTypeFactory().constructCollectionType(List.class, BybitTrade.class)
                    );
                    return Observable.fromIterable(BybitStreamAdapters.adaptTrades(bybitTradeList, instrument).getTrades());
                });
    }

    @Override
    public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
        String channelUniqueId = TICKER + convertToBybitSymbol(instrument);
        return streamingService
                .subscribeChannel(channelUniqueId)
                .filter(jsonNode -> jsonNode.has("data"))
                .flatMap(jsonNode -> {
                    try {
                        JsonNode data = jsonNode.get("data");
                        String symbol = data.get("symbol").asText();
                        BigDecimal bidPrice = safeGetDecimal(data, "bidPrice");
                        BigDecimal bidSize = safeGetDecimal(data, "bidSize");
                        BigDecimal askPrice = safeGetDecimal(data, "askPrice");
                        BigDecimal askSize = safeGetDecimal(data, "askSize");
                        BigDecimal lastPrice = safeGetDecimal(data, "lastPrice");
                        BigDecimal highPrice24h = safeGetDecimal(data, "highPrice24h");
                        BigDecimal lowPrice24h = safeGetDecimal(data, "lowPrice24h");
                        BigDecimal volume24h = safeGetDecimal(data, "volume24h");

                        CurrencyPair currencyPair = getBybitCurrencyPair(symbol);
                        Ticker ticker = new Ticker.Builder()
                                .currencyPair(currencyPair)
                                .ask(askPrice)
                                .askSize(askSize)
                                .bid(bidPrice)
                                .bidSize(bidSize)
                                .last(lastPrice)
                                .high(highPrice24h)
                                .low(lowPrice24h)
                                .volume(volume24h)
                                .timestamp(new Date())
                                .build();
                        return Observable.just(ticker);
                    } catch (Exception e) {
                        LOG.error("getTicker exception {}", jsonNode, e);
                        return Observable.empty();
                    }
                });
    }

    private BigDecimal safeGetDecimal(JsonNode node, String fieldName) {
        return node.hasNonNull(fieldName) ? new BigDecimal(node.get(fieldName).asText()) : BigDecimal.ZERO;
    }

    private CurrencyPair getBybitCurrencyPair(String symbol) {
        String formattedSymbol = symbol.replace("SPOT_", "");
        String[] parts = formattedSymbol.split("-");
        String pairString = parts.length > 0 ? parts[0] : formattedSymbol;

        String baseCurrency;
        String counterCurrency;

        if (pairString.endsWith("USDT")) {
            baseCurrency = pairString.substring(0, pairString.length() - 4);
            counterCurrency = "USDT";
        } else if (pairString.endsWith("USD")) {
            baseCurrency = pairString.substring(0, pairString.length() - 3);
            counterCurrency = "USD";
        } else if (pairString.endsWith("BUSD")) {
            baseCurrency = pairString.substring(0, pairString.length() - 4);
            counterCurrency = "BUSD";
        } else {
            baseCurrency = pairString.substring(0, 3);
            counterCurrency = pairString.substring(3);
        }
        return new CurrencyPair(baseCurrency, counterCurrency);
    }
}
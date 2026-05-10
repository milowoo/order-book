package com.orderbook.core.exchange.handler;

import cn.hutool.core.collection.CollUtil;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.bitget.BitgetAdapters;
import com.orderbook.connector.interfaces.StreamConnectorFactory;
import com.orderbook.connector.stream.bitget.BitgetStreamingExchange;
import com.orderbook.connector.common.dto.BitgetAccountNotification;
import com.orderbook.connector.common.dto.BitgetOrderNotification;
import com.orderbook.connector.common.dto.BitgetTickerNotification;
import com.orderbook.connector.common.dto.BitgetWsOrderBookSnapshotNotification;
import com.orderbook.connector.common.dto.BitgetChannel;
import com.orderbook.connector.stream.bitget.BitgetStreamingService;
import com.orderbook.core.config.ExchangeConnectConfig;
import com.orderbook.core.domain.*;
import com.orderbook.core.exchange.disruptor.OrderBookDisruptor;
import com.orderbook.core.store.AccountStore;
import com.orderbook.core.store.OpenOrdersStore;
import com.orderbook.core.store.PriceStore;
import com.orderbook.core.store.SymbolStore;
import io.reactivex.rxjava3.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BitgetHandler {
    @Autowired
    private OrderBookDisruptor orderBookDisruptor;
    @Autowired
    private StreamConnectorFactory streamConnectorFactory;
    @Autowired
    private ExchangeConnectConfig exchangeConnectConfig;
    @Autowired
    private SymbolStore symbolStore;
    @Autowired
    private PriceStore priceStore;

    private BitgetStreamingService publicStreamingService;
    private final Map<CurrencyPair, Disposable> orderBookObservableMap = new ConcurrentHashMap<>();
    private final List<Disposable> disposables = new ArrayList<>();

    private static ExchangeCode getExchange() {
        return ExchangeCode.BITGET;
    }

    public void dispose() {
        log.info("Disposing all subscriptions...");
        disposables.forEach(Disposable::dispose);
        disposables.clear();
    }

    @PreDestroy
    public void onExit() {
        dispose();
    }

    @PostConstruct
    public void init() {
        List<SymbolBo> symbolBos = symbolStore.getActiveSymbols();
        if (CollUtil.isEmpty(symbolBos)) {
            return;
        }

        ExchangeInfo exchangeInfo = exchangeConnectConfig.getExchangeInfo(getExchange().name());
        if (exchangeInfo == null) {
            log.warn("No connect configured for {}", getExchange().name());
            return;
        }

        BitgetStreamingExchange authExchange = (BitgetStreamingExchange) streamConnectorFactory.getExchange(getExchange(), true);
        if (!authExchange.isAlive()) {
            authExchange.connect().blockingAwait();
        }
        publicStreamingService = authExchange.getPublicStreamingService();

        symbolBos.forEach(a -> {
            CurrencyPair currencyPair = new CurrencyPair(a.getBaseTokenId(), a.getQuoteTokenId());
            //订阅order book
            Disposable orderbookDisposable = orderBookObservableMap.computeIfAbsent(currencyPair, this::subscribeOrderBook);
            disposables.add(orderbookDisposable);

            //订阅ticker
            Disposable subscribeTicker = authExchange.getPublicStreamingService()
                    .subscribeChannel(null, BitgetChannel.ChannelType.TICKER,
                            BitgetChannel.MarketType.SPOT, currencyPair)
                    .map(BitgetTickerNotification.class::cast)
                    .doOnError(e -> log.error("BitgetHandler ticker subscribe symbol:{} error:{}",
                            currencyPair.toString(), e.getMessage()))
                    .subscribe(this::handlerTicker);
            disposables.add(subscribeTicker);

            //订阅order
            Disposable orderSubscribe = authExchange.getPrivateStreamingService()
                    .subscribeChannel(null, BitgetChannel.ChannelType.ORDERS,
                            BitgetChannel.MarketType.SPOT, null)
                    .map(BitgetOrderNotification.class::cast)
                    .doOnError(e -> log.error("BitgetHandler order subscribe symbol:{} error:{}",
                            "default", e.getMessage()))
                    .subscribe(this::handlerOrder);
            disposables.add(orderSubscribe);

            //订阅account
            Disposable accountSubscribe = authExchange.getPrivateStreamingService()
                    .subscribeChannel(null, BitgetChannel.ChannelType.ACCOUNT,
                            BitgetChannel.MarketType.SPOT, null)
                    .map(BitgetAccountNotification.class::cast)
                    .doOnError(e -> log.error("BitgetHandler account subscribe symbol:{} error:{}",
                            "default", e.getMessage()))
                    .subscribe(this::handlerAccount);
            disposables.add(accountSubscribe);
        });
    }

    public void handlerOrderBook(BitgetWsOrderBookSnapshotNotification notification) {
        try {
            if (Objects.isNull(notification)
                    || Objects.isNull(notification.getChannel())
                    || Objects.isNull(notification.getChannel().getInstrumentId())
                    || CollUtil.isEmpty(notification.getPayloadItems())) {
                return;
            }

            BitgetChannel channel = notification.getChannel();
            SymbolBo symbolBo = symbolStore.findSymbolById(channel.getInstrumentId());
            if (Objects.isNull(symbolBo)) {
                return;
            }

            OrderBook.OrderBookBuilder builder = OrderBook.builder()
                    .symbol(symbolBo.getSymbolId())
                    .exchange(getExchange());

            notification.getPayloadItems().forEach(item -> {
                List<PriceLevel> asks = item.getAsks().stream()
                        .map(ask -> new PriceLevel(ask.getPrice(), ask.getSize()))
                        .collect(Collectors.toList());
                List<PriceLevel> bids = item.getBids().stream()
                        .map(bid -> new PriceLevel(bid.getPrice(), bid.getSize()))
                        .collect(Collectors.toList());
                builder.ask(asks);
                builder.bid(bids);
                builder.checksum(item.getChecksum());
                orderBookDisruptor.publish(builder.build());
            });
        } catch (Exception e) {
            log.error("BitgetHandler order book message:{} error:{}", notification, e.getMessage());
        }
    }

    public void handlerTicker(BitgetTickerNotification ticker) {
        try {
            if (Objects.isNull(ticker) || CollUtil.isEmpty(ticker.getPayloadItems())) {
                return;
            }
            ticker.getPayloadItems().forEach(item -> {
                SymbolBo symbolBo = symbolStore.findSymbolById(item.getInstrument());
                if (Objects.nonNull(symbolBo)) {
                    priceStore.setPrice(getExchange(), symbolBo.getSymbolId(), item.getLastPrice());
                    priceStore.setLastTime(getExchange(), symbolBo.getSymbolId(), item.getTimestamp().toEpochMilli());
                }
            });
        } catch (Exception e) {
            log.error("BitgetHandler ticker message:{} error:{}", ticker, e.getMessage());
        }
    }

    public void handlerOrder(BitgetOrderNotification orderNotification) {
        try {
            if (Objects.isNull(orderNotification) || CollUtil.isEmpty(orderNotification.getPayloadItems())) {
                return;
            }
            orderNotification.getPayloadItems().forEach(item -> {
                SymbolBo symbolBo = symbolStore.findSymbolById(item.getInstrument());
                if (Objects.isNull(symbolBo)) {
                    return;
                }
                OpenOrdersBo openOrdersBo = OpenOrdersStore.getOpenOrders(getExchange(), symbolBo.getSymbolId());
                if (openOrdersBo == null) {
                    return;
                }
                OrderBo orderBo = OrderBo.builder()
                        .orderId(Long.valueOf(item.getOrderId()))
                        .clientOrderId(item.getClientOid())
                        .symbolId(symbolBo.getSymbolId())
                        .orderStatus(BitgetAdapters.toOrderStatus(item.getStatus()).name())
                        .orderType(item.getOrdType())
                        .side(item.getSide().name())
                        .price(new BigDecimal(item.getPrice()))
                        .quantity(new BigDecimal(item.getSize()))
                        .amount(new BigDecimal(item.getPrice()).multiply(new BigDecimal(item.getSize())))
                        .dealAmount(new BigDecimal(item.getAccBaseVolume()))
                        .dealPriceAvg(new BigDecimal(item.getPriceAvg()))
                        .dealQuantity(new BigDecimal(item.getAccBaseVolume()))
                        .build();
                orderBo.setRemainingQty(orderBo.getQuantity().subtract(orderBo.getDealQuantity()));
                orderBo.setRemainingAmount(orderBo.getAmount().subtract(orderBo.getDealAmount()));
                openOrdersBo.updateOpenOrder(orderBo);
            });
        } catch (Exception e) {
            log.error("BitgetHandler order notification message:{} error:{}", orderNotification, e.getMessage());
        }
    }

    public void handlerAccount(BitgetAccountNotification accountNotification) {
        try {
            if (Objects.isNull(accountNotification) || CollUtil.isEmpty(accountNotification.getPayloadItems())) {
                return;
            }
            TreeSet<String> coins = new TreeSet<>();
            symbolStore.getActiveSymbols().forEach(config -> {
                coins.add(config.getBaseTokenId());
                coins.add(config.getQuoteTokenId());
            });
            accountNotification.getPayloadItems().stream()
                    .filter(item -> coins.contains(item.getCoin()))
                    .forEach(item -> {
                        BalanceBo balanceBo = BalanceBo.builder()
                                .coin(item.getCoin())
                                .available(new BigDecimal(item.getAvailable()))
                                .locked(new BigDecimal(item.getLocked()).add(new BigDecimal(item.getFrozen())))
                                .updateTime(Long.parseLong(item.getUTime()))
                                .build();
                        balanceBo.setTotal(balanceBo.getAvailable().add(balanceBo.getLocked()));
                        AccountStore.getAccount(getExchange()).setBalance(balanceBo);
                    });
        } catch (Exception e) {
            log.error("BitgetHandler account notification message:{} error:{}", accountNotification, e.getMessage());
        }
    }

    public void rebuildOrderBook(String symbol) {
        if (StringUtils.isBlank(symbol) || Objects.isNull(publicStreamingService)) {
            return;
        }
        CurrencyPair currencyPair = new CurrencyPair(symbol);
        orderBookObservableMap.compute(currencyPair, (key, value) -> {
            if (Objects.nonNull(value) && !value.isDisposed()) {
                value.dispose();
            }
            return subscribeOrderBook(currencyPair);
        });
    }

    public Disposable subscribeOrderBook(CurrencyPair currencyPair) {
        return publicStreamingService
                .subscribeChannel(null, BitgetChannel.ChannelType.DEPTH,
                        BitgetChannel.MarketType.SPOT, currencyPair)
                .map(BitgetWsOrderBookSnapshotNotification.class::cast)
                .doOnError(e -> log.error("BitgetHandler order book subscribe symbol:{} error:{}",
                        currencyPair.toString(), e.getMessage()))
                .subscribe(this::handlerOrderBook);
    }
}
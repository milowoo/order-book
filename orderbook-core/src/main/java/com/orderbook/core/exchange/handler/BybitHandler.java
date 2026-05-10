package com.orderbook.core.exchange.handler;

import cn.hutool.core.collection.CollUtil;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.interfaces.StreamConnectorFactory;
import com.orderbook.connector.stream.bybit.BybitStreamingExchange;
import com.orderbook.connector.stream.bybit.BybitStreamingMarketDataService;
import com.orderbook.connector.stream.bybit.BybitStreamingTradeService;
import com.orderbook.core.config.ExchangeConnectConfig;
import com.orderbook.core.domain.*;
import com.orderbook.core.exchange.disruptor.OrderBookDisruptor;
import com.orderbook.core.store.AccountStore;
import com.orderbook.core.store.OpenOrdersStore;
import com.orderbook.core.store.PriceStore;
import com.orderbook.core.store.SymbolStore;
import io.reactivex.rxjava3.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BybitHandler {
    @Autowired
    ExchangeConnectConfig exchangeConnectConfig;
    @Autowired
    private OrderBookDisruptor orderBookDisruptor;
    @Autowired
    @Qualifier("streamConnectorFactory")
    private StreamConnectorFactory streamConnectorFactory;
    @Autowired
    PriceStore priceStore;
    @Autowired
    SymbolStore symbolStore;

    private BybitStreamingExchange pubExchange = null;
    private BybitStreamingExchange priExchange = null;
    private Set<String> subscribeSymbolPub = new HashSet<>();
    private Set<String> subscribeSymbolPri = new HashSet<>();
    private final List<Disposable> disposables = new ArrayList<>();

    private static ExchangeCode getExchange() {
        return ExchangeCode.BYBIT;
    }

    @PostConstruct
    public void init() {
        log.info("BybitHandler init begin ");
        List<SymbolBo> symbolBos = symbolStore.getActiveSymbols();
        if (CollUtil.isEmpty(symbolBos)) {
            return;
        }

        ExchangeInfo exchangeInfo = exchangeConnectConfig.getExchangeInfo(getExchange().name());
        if (exchangeInfo == null) {
            log.warn("No connect configured for {}", getExchange().name());
            return;
        }
        try {
            if (exchangeInfo.getStreamPublicUse()) {
                // 初始化公有数据连接并订阅
                pubExchange = (BybitStreamingExchange) streamConnectorFactory.getExchange(getExchange(), false);
                pubExchange.connect().blockingAwait();
                initPublicSubscriptions(symbolBos);
            }
            if (exchangeInfo.getStreamPrivateUse()) {
                // 初始化私有数据连接并订阅
                priExchange = (BybitStreamingExchange) streamConnectorFactory.getExchange(getExchange(), true);
                priExchange.connect().blockingAwait();
                initPrivateSubscriptions(symbolBos);
            }
        } catch (Exception e) {
            log.error("Failed to initialize BybitHandler", e);
            throw e;
        }
        log.info("BybitHandler init end ");
    }

    //共有行情订阅
    private void initPublicSubscriptions(List<SymbolBo> symbolBos) {
        if (pubExchange == null) {
            return;
        }
        BybitStreamingMarketDataService marketDataService = pubExchange.getStreamingMarketDataService();
        symbolBos.forEach(symbol -> {
            CurrencyPair pair = new CurrencyPair(symbol.getBaseTokenId(), symbol.getQuoteTokenId());
            Instrument instrument = pair;
            Disposable orderBookSub = marketDataService.getOrderBook(instrument, "200")
                    .map(OrderBook.class::cast)
                    .doOnError((Throwable e) -> log.error("OrderBook subscribe failed for {}, error: {}",
                            pair, e.getMessage()))
                    .subscribe(this::handlerOrderBook);
            disposables.add(orderBookSub);

            Disposable tickerSub = marketDataService.getTicker(instrument)
                    .map(Ticker.class::cast)
                    .doOnError((Throwable e) -> log.error("Ticker subscribe failed for {}, error: {}",
                            pair, e.getMessage()))
                    .subscribe(this::handlerTicker);
            disposables.add(tickerSub);
            subscribeSymbolPub.add(symbol.getSymbolId());
        });
    }

    //私有行情订阅
    private void initPrivateSubscriptions(List<SymbolBo> symbolBos) {
        if (priExchange == null) {
            return;
        }
        BybitStreamingTradeService tradeService = priExchange.getStreamingTradeService();
        symbolBos.forEach(symbol -> {
            CurrencyPair pair = new CurrencyPair(symbol.getBaseTokenId(), symbol.getQuoteTokenId());
            Disposable orderSub = tradeService.getOrderChanges(pair, BybitCategory.SPOT)
                    .map(Order.class::cast)
                    .doOnError((Throwable e) -> log.error("Order update subscribe failed for {}, error: {}",
                            pair, e.getMessage()))
                    .subscribe(this::handlerOrder);
            disposables.add(orderSub);
            subscribeSymbolPri.add(symbol.getSymbolId());
        });

        // Subscribe balance
        Disposable balanceSub = priExchange.getStreamingAccountService()
                .getBalanceChanges(Currency.USDT)
                .subscribe(this::handlerBalance);
        disposables.add(balanceSub);
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

    private String getOrderBookSymbol(OrderBook orderBook) {
        if (!orderBook.getAsks().isEmpty()) {
            LimitOrder limitOrder = orderBook.getAsks().get(0);
            return limitOrder.getInstrument().toString();
        }
        LimitOrder limitOrder = orderBook.getBids().get(0);
        return limitOrder.getInstrument().toString();
    }

    //处理order book
    public void handlerOrderBook(OrderBook orderBook) {
        if (Objects.isNull(orderBook) || (orderBook.getAsks().isEmpty() && orderBook.getBids().isEmpty())) {
            return;
        }
        String symbol = getOrderBookSymbol(orderBook);
        com.orderbook.core.domain.OrderBook.OrderBookBuilder builder =
                com.orderbook.core.domain.OrderBook.builder()
                        .symbol(symbol)
                        .exchange(getExchange());

        List<PriceLevel> asks = orderBook.getAsks().stream().map(ask ->
                new PriceLevel(ask.getLimitPrice(), ask.getOriginalAmount())
        ).collect(Collectors.toList());
        builder.ask(asks);

        List<PriceLevel> bids = orderBook.getBids().stream().map(bid ->
                new PriceLevel(bid.getLimitPrice(), bid.getOriginalAmount())
        ).collect(Collectors.toList());
        builder.bid(bids);
        // log.info("Receive bybit order book ask size {} bid size {}", asks.size(), bids.size());

        //推送disruptor处理缓存
        orderBookDisruptor.publish(builder.build());
    }

    private void handlerTicker(Ticker ticker) {
        if (Objects.isNull(ticker)) {
            return;
        }
        String symbol = ticker.getInstrument().toString();
        priceStore.setPrice(getExchange(), symbol, ticker.getLast());
        //todo place getTimestamp 现在为空
        priceStore.setLastTime(getExchange(), symbol, ticker.getTimestamp().getTime());
    }

    private void handlerOrder(Order order) {
        log.info("bybit OrderHandler order subscribe order {} ", order);
        String symbol = order.getInstrument().toString();
        OpenOrdersBo openOrdersBo = OpenOrdersStore.getOpenOrders(getExchange(), symbol);
        if (openOrdersBo == null) {
            log.error("handlerOrder get open order store err {}", order);
            return;
        }
        openOrdersBo.updateOpenOrder(convertOrder((LimitOrder) order));
    }

    private OrderBo convertOrder(LimitOrder order) {
        OrderBo orderBo = new OrderBo();
        orderBo.setClientOrderId(order.getUserReference());
        orderBo.setOrderId(Long.parseLong(order.getId()));
        orderBo.setSymbolId(order.getInstrument().toString());
        if ("bid".equalsIgnoreCase(order.getType().name()) || "EXIT_BID".equalsIgnoreCase(order.getType().name())) {
            orderBo.setSide("buy");
        } else {
            orderBo.setSide("sell");
        }

        BigDecimal price = order.getLimitPrice() == null ? order.getAveragePrice() : order.getLimitPrice();
        orderBo.setPrice(price);
        orderBo.setQuantity(order.getOriginalAmount());
        orderBo.setAmount(order.getOriginalAmount());
        orderBo.setRemainingQty(order.getRemainingAmount() == null ? order.getOriginalAmount() : order.getRemainingAmount());
        orderBo.setRemainingAmount(order.getRemainingAmount());
        orderBo.setCreateTime(order.getTimestamp().getTime());
        return orderBo;
    }

    private void handlerBalance(Balance balance) {
        log.info("bybit handlerBalance {}", balance);
        BalanceBo balanceBo = BalanceBo.builder()
                .coin("USDT")
                .available(balance.getAvailable())
                .locked(balance.getFrozen())
                .updateTime(balance.getTimestamp().getTime())
                .build();
        balanceBo.setTotal(balanceBo.getAvailable().add(balanceBo.getLocked()));
        //写入缓存
        AccountStore.getAccount(getExchange()).setBalance(balanceBo);
    }

    @Scheduled(initialDelay = 2 * 60 * 1000, fixedDelay = 3 * 60 * 1000)
    private void autoSubscribeSymbol() {
        List<SymbolBo> symbolBos = symbolStore.getActiveSymbols();
        if (symbolBos.isEmpty()) {
            return;
        }
        List<SymbolBo> symbolsPub = symbolBos.stream()
                .filter(Objects::nonNull)
                .filter(symbol -> !subscribeSymbolPub.contains(symbol.getSymbolId()))
                .collect(Collectors.toList());
        if (!symbolsPub.isEmpty()) {
            initPublicSubscriptions(symbolsPub);
        }

        List<SymbolBo> symbolsPri = symbolBos.stream()
                .filter(Objects::nonNull)
                .filter(symbol -> !subscribeSymbolPri.contains(symbol.getSymbolId()))
                .collect(Collectors.toList());
        if (!symbolsPri.isEmpty()) {
            initPrivateSubscriptions(symbolsPub);
        }
    }
}
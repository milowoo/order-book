package com.orderbook.core.exchange.handler;

import cn.hutool.core.collection.CollUtil;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.domain.ExchangeInfo;
import com.orderbook.connector.interfaces.StreamConnectorFactory;
import com.orderbook.connector.stream.binance.BinanceStreamingExchange;
import com.orderbook.core.config.ExchangeConnectConfig;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.exchange.disruptor.OrderBookDisruptor;
import com.orderbook.core.domain.*;
import com.orderbook.core.service.PnlService;
import com.orderbook.core.service.FeeService;
import com.orderbook.core.store.AccountStore;
import com.orderbook.core.store.OpenOrdersStore;
import com.orderbook.core.store.PriceStore;
import com.orderbook.core.store.SymbolStore;
import info.bitrich.xchangestream.core.ProductSubscription;
import io.reactivex.rxjava3.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.binance.service.BinanceTradeService;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BinanceHandler {
    @Autowired
    private OrderBookDisruptor orderBookDisruptor;
    @Autowired
    ExchangeConnectConfig exchangeConnectConfig;
    @Autowired
    @Qualifier("streamConnectorFactory")
    private StreamConnectorFactory streamConnectorFactory;
    @Autowired
    OpenOrdersStore openOrdersStore;
    @Autowired
    SymbolStore symbolStore;
    @Autowired
    PriceStore priceStore;
    @Autowired
    private FeeService feeService;

    BinanceStreamingExchange pubExchange = null;
    BinanceStreamingExchange priExchange = null;
    private final List<Disposable> disposables = new ArrayList<>();
    private Set<String> subscribeSymbolPub = new HashSet<>();
    private Set<String> subscribeSymbolPri = new HashSet<>();

    // Heartbeat tracking
    private final AtomicLong lastMessageTime = new AtomicLong(System.currentTimeMillis());
    private static final long HEARTBEAT_TIMEOUT_MS = 60_000L;

    // PnL tracking
    @Autowired
    private PnlService pnlService;
    private final ConcurrentMap<String, BigDecimal> lastDealQty = new ConcurrentHashMap<>();

    private static ExchangeCode getExchange() {
        return ExchangeCode.BINANCE;
    }

    @PostConstruct
    public void init() {
        connectAndSubscribe();
    }

    void connectAndSubscribe() {
        log.info("BinanceHandler init begin");
        List<SymbolBo> symbolBos = symbolStore.getActiveSymbols();
        if (CollUtil.isEmpty(symbolBos)) {
            log.warn("No symbols configured for {}", getExchange().name());
            return;
        }

        ExchangeInfo exchangeInfo = exchangeConnectConfig.getExchangeInfo(getExchange().name());
        if (exchangeInfo == null) {
            log.warn("No connect configured for {}", getExchange().name());
            return;
        }
        log.info("BinanceHandler test {}", exchangeInfo);
        try {
            if (exchangeInfo.getStreamPublicUse()) {
                setupPubExchangeConnection(symbolBos);
            }
            if (exchangeInfo.getStreamPrivateUse()) {
                setupPriExchangeConnection(symbolBos);
            }
        } catch (Exception e) {
            log.error("Failed to initialize Binance handler", e);
            throw e;
        }
        lastMessageTime.set(System.currentTimeMillis());
        log.info("BinanceHandler init end");
    }

    /** Check connection health and reconnect if needed. */
    @Scheduled(fixedDelay = 30_000)
    public void checkConnection() {
        boolean pubAlive = pubExchange == null || pubExchange.isAlive();
        boolean priAlive = priExchange == null || priExchange.isAlive();
        long idle = System.currentTimeMillis() - lastMessageTime.get();
        if (!pubAlive || !priAlive || idle > HEARTBEAT_TIMEOUT_MS) {
            log.warn("[Binance] Connection unhealthy: pubAlive={}, priAlive={}, idle={}ms", pubAlive, priAlive, idle);
            reconnect();
        }
    }

    private synchronized void reconnect() {
        log.warn("[Binance] Reconnecting...");
        dispose();
        if (pubExchange != null) { try { pubExchange.disconnect(); } catch (Exception e) { /* ignore */ } }
        if (priExchange != null) { try { priExchange.disconnect(); } catch (Exception e) { /* ignore */ } }
        pubExchange = null;
        priExchange = null;
        lastDealQty.clear();
        connectAndSubscribe();
        lastMessageTime.set(System.currentTimeMillis());
        log.warn("[Binance] Reconnect complete");
    }

    /**
     * Check if the exchange connection is healthy.
     */
    public boolean isConnectionHealthy() {
        boolean pubAlive = pubExchange == null || pubExchange.isAlive();
        boolean priAlive = priExchange == null || priExchange.isAlive();
        return pubAlive && priAlive;
    }

    private void setupPriExchangeConnection(List<SymbolBo> symbolBos) {
        List<Instrument> instruments = symbolBos.stream()
                .map(symbol -> new CurrencyPair(
                        symbol.getBaseTokenId(),
                        symbol.getQuoteTokenId()))
                .collect(Collectors.toList());

        ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();
        // 遍历列表，逐个添加
        for (Instrument instrument : instruments) {
            builder.addOrders(instrument);
        }

        priExchange = (BinanceStreamingExchange) streamConnectorFactory.getExchange(getExchange(), true);
        priExchange.connect(builder.build()).blockingAwait();
        subscribeToUserData(symbolBos);

        // Subscribe balance
        Disposable balanceSub = priExchange.getStreamingAccountService()
                .getBalanceChanges(Currency.USDT)
                .subscribe(this::handlerBalance);
        disposables.add(balanceSub);
    }

    //私有行情订阅处理
    private void subscribeToUserData(List<SymbolBo> symbolBos) {
        if (priExchange == null) {
            return;
        }
        symbolBos.forEach(symbol -> {
            CurrencyPair pair = new CurrencyPair(symbol.getBaseTokenId(), symbol.getQuoteTokenId());
            // Subscribe Order
            Disposable orderSub = priExchange.getStreamingTradeService()
                    .getOrderChanges(pair)
                    .subscribe(this::handlerOrder);
            disposables.add(orderSub);
        });
    }

    private void handlerOrder(Order order) {
        lastMessageTime.set(System.currentTimeMillis());
        log.debug("BinanceHandler order subscribe order {}", order);
        String symbol = order.getInstrument().toString();
        OpenOrdersBo openOrdersBo = openOrdersStore.getOpenOrders(getExchange(), symbol);
        if (openOrdersBo == null) {
            return;
        }
        OrderBo orderBo = convertOrder((LimitOrder) order);
        openOrdersBo.updateOpenOrder(orderBo);

        // Track fills for PnL
        BigDecimal dealQty = orderBo.getDealQuantity();
        BigDecimal lastQty = lastDealQty.getOrDefault(orderBo.getClientOrderId(), BigDecimal.ZERO);
        if (dealQty != null && dealQty.compareTo(lastQty) > 0) {
            BigDecimal newFillQty = dealQty.subtract(lastQty);
            lastDealQty.put(orderBo.getClientOrderId(), dealQty);
            // Use Order.fee if available, otherwise estimate from fee_config
            BigDecimal fee = order.getFee() != null ? order.getFee() : BigDecimal.ZERO;
            if (fee.compareTo(BigDecimal.ZERO) == 0 && orderBo.getDealPriceAvg() != null) {
                BigDecimal notional = orderBo.getDealPriceAvg().multiply(newFillQty);
                fee = feeService.estimateFee(getExchange(), symbol, notional);
            }
            pnlService.recordFill(symbol, orderBo.getSide(),
                    orderBo.getDealPriceAvg() != null ? orderBo.getDealPriceAvg() : orderBo.getPrice(),
                    newFillQty, fee, getExchange().name(),
                    orderBo.getClientOrderId() + "-" + System.currentTimeMillis());
        }
    }

    private OrderBo convertOrder(LimitOrder order) {
        OrderBo orderBo = new OrderBo();
        String clientOrderId = order.getOrderFlags().stream()
                .filter(Order.IOrderFlags.class::isInstance)
                .map(orderFlag -> ((BinanceTradeService.BinanceOrderFlags) orderFlag).getClientId())
                .findFirst()
                .orElse(null);
        orderBo.setClientOrderId(clientOrderId);
        if (order.getId() != null) {
            orderBo.setOrderId(Long.parseLong(order.getId()));
        }
        orderBo.setOrderStatus(order.getStatus().name());

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

        // Populate deal fields from cumulative executed values
        if (order.getCumulativeAmount() != null) {
            orderBo.setDealQuantity(order.getCumulativeAmount());
        }
        if (order.getAveragePrice() != null) {
            orderBo.setDealPriceAvg(order.getAveragePrice());
        }
        if (order.getCumulativeAmount() != null && order.getAveragePrice() != null) {
            orderBo.setDealAmount(order.getCumulativeAmount().multiply(order.getAveragePrice()));
        }

        orderBo.setCreateTime(order.getTimestamp() != null ? order.getTimestamp().getTime() : System.currentTimeMillis());
        return orderBo;
    }

    //公有行情订阅处理
    private void setupPubExchangeConnection(List<SymbolBo> symbolBos) {
        List<Instrument> instruments = symbolBos.stream()
                .map(symbol -> new CurrencyPair(
                        symbol.getBaseTokenId(),
                        symbol.getQuoteTokenId()))
                .collect(Collectors.toList());

        ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();
        // 遍历列表，逐个添加
        for (Instrument instrument : instruments) {
            builder.addOrderbook(instrument);
            builder.addTicker(instrument);
        }

        pubExchange = (BinanceStreamingExchange) streamConnectorFactory.getExchange(getExchange(), false);
        pubExchange.connect(builder.build()).blockingAwait();
        subscribeToMarkets(symbolBos);
    }

    private void subscribeToMarkets(List<SymbolBo> symbolBos) {
        if (pubExchange == null) {
            return;
        }
        symbolBos.forEach(symbol -> {
            CurrencyPair pair = new CurrencyPair(symbol.getBaseTokenId(), symbol.getQuoteTokenId());
            // Subscribe Order Book
            Disposable depthSub = pubExchange.getStreamingMarketDataService()
                    .getOrderBook(pair)
                    .subscribe(this::handlerOrderBook);
            disposables.add(depthSub);

            // Enable live subscription
            pubExchange.enableLiveSubscription();

            // Subscribe Ticker
            Disposable tickerSub = pubExchange.getStreamingMarketDataService()
                    .getTicker(pair)
                    .subscribe(this::handlerTicker);
            disposables.add(tickerSub);

            subscribeSymbolPub.add(symbol.getSymbolId());
        });
    }

    public void dispose() {
        log.info("Disposing all subscriptions...");
        disposables.forEach(Disposable::dispose);
        disposables.clear();
    }

    @PreDestroy
    public void onExit() {
        dispose();
        if (pubExchange != null) { try { pubExchange.disconnect(); } catch (Exception e) { /* ignore */ } }
        if (priExchange != null) { try { priExchange.disconnect(); } catch (Exception e) { /* ignore */ } }
    }

    public void handlerOrderBook(OrderBook orderBook) {
        lastMessageTime.set(System.currentTimeMillis());
        if (Objects.isNull(orderBook) || (orderBook.getAsks().isEmpty() && orderBook.getBids().isEmpty())) {
            return;
        }
        log.info("Receive binance order book ask size {} bid size {}", orderBook.getAsks().size(), orderBook.getBids().size());
        LimitOrder limitOrder = orderBook.getAsks().get(0);
        String symbol = limitOrder.getInstrument().toString();
        com.orderbook.core.domain.OrderBook.OrderBookBuilder builder =
                com.orderbook.core.domain.OrderBook.builder()
                        .symbol(symbol)
                        .exchange(getExchange());

        //构建处理订单簿缓存参数
        List<PriceLevel> asks = orderBook.getAsks().stream().map(ask ->
                new PriceLevel(ask.getLimitPrice(), ask.getOriginalAmount())
        ).collect(Collectors.toList());
        builder.ask(asks);

        List<PriceLevel> bids = orderBook.getBids().stream().map(bid ->
                new PriceLevel(bid.getLimitPrice(), bid.getOriginalAmount())
        ).collect(Collectors.toList());
        builder.bid(bids);

        //推送disruptor处理缓存
        orderBookDisruptor.publish(builder.build());
    }

    private void handlerTicker(Ticker ticker) {
        lastMessageTime.set(System.currentTimeMillis());
        if (Objects.isNull(ticker)) {
            return;
        }
        String symbol = ticker.getInstrument().toString();
        priceStore.setPrice(getExchange(), symbol, ticker.getLast());
        priceStore.setLastTime(getExchange(), symbol, ticker.getTimestamp().getTime());
    }

    private void handlerBalance(Balance balance) {
        lastMessageTime.set(System.currentTimeMillis());
        if (Objects.isNull(balance)) {
            return;
        }
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
        List<SymbolBo> symbolPub = symbolBos.stream()
                .filter(Objects::nonNull)
                .filter(symbol -> !subscribeSymbolPub.contains(symbol.getSymbolId()))
                .collect(Collectors.toList());

        List<SymbolBo> symbolPri = symbolBos.stream()
                .filter(Objects::nonNull)
                .filter(symbol -> !subscribeSymbolPri.contains(symbol.getSymbolId()))
                .collect(Collectors.toList());

        if (!symbolPub.isEmpty()) {
            subscribeToMarkets(symbolPub);
        }
        if (!symbolPri.isEmpty()) {
            subscribeToUserData(symbolPri);
        }
    }
}
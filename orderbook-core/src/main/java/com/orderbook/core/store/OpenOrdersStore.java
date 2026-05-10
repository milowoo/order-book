package com.orderbook.core.store;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Maps;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.global.service.GlobalTradeService;
import com.orderbook.connector.interfaces.ConnectorFactory;
import com.orderbook.core.config.ExchangeConnectConfig;
import com.orderbook.core.config.StrategyProps;
import com.orderbook.core.domain.ExchangeInfo;
import com.orderbook.core.domain.OpenOrdersBo;
import com.orderbook.core.domain.OrderBo;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.utils.OrderUtils;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OpenOrdersStore {
    @Autowired
    StrategyProps symbolConfig;

    @Autowired
    ExchangeConnectConfig exchangeConnectConfig;

    @Autowired
    private ConnectorFactory connectorFactory;

    private static final Map<String, OpenOrdersBo> openOrders = Maps.newConcurrentMap();

    private static String getKey(String exchangeName, String symbolId) {
        return exchangeName + "_" + symbolId;
    }

    public static OpenOrdersBo getOpenOrders(ExchangeCode exchangeCode, String symbol) {
        String key = getKey(exchangeCode.name(), symbol);
        return openOrders.computeIfAbsent(key, (String k) -> new OpenOrdersBo(symbol));
    }

    private static ExchangeCode getExchange() {
        return ExchangeCode.OSL_GLOBAL;
    }

    public void setOpenOrders(ExchangeCode exchangeCode, SymbolBo symbol, OpenOrdersBo openOrdersBo) {
        String key = getKey(exchangeCode.name(), symbol.getSymbolId());
        openOrders.put(key, openOrdersBo);
    }

    @PostConstruct
    public void init() {
        List<SymbolBo> configs = symbolConfig.getActiveSymbols();
        if (CollUtil.isEmpty(configs)) {
            log.error("No exchange symbol config found. Price processing cannot be carried out.");
            throw new IllegalStateException("No active symbol config found. Aborting initialization.");
        }

        for (SymbolBo symbolBo : configs) {
            ExchangeInfo exchangeInfo = exchangeConnectConfig.getExchangeInfo(getExchange().name());
            if (exchangeInfo == null || !exchangeInfo.getStreamPrivateUse()) {
                continue;
            }

            boolean success = false;
            int retryCount = 0;
            while (!success && retryCount < 3) {
                try {
                    processSymbolOrders(symbolBo);
                    success = true;
                } catch (Exception e) {
                    retryCount++;
                    log.warn("Attempt {} failed to initialize open orders for symbol {}: {}",
                            retryCount, symbolBo.getSymbolId(), e.getMessage(), e);
                    if (retryCount >= 3) {
                        throw new IllegalStateException(
                                String.format("Failed to initialize open orders for symbol %s after 3 attempts.",
                                        symbolBo.getSymbolId()), e);
                    }
                }
                try {
                    Thread.sleep(1000); // 每次重试之间等待1秒
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Retry interrupted during init.", ie);
                }
            }
        }
    }

    private void processSymbolOrders(SymbolBo symbol) throws IOException {
        ExchangeCode exchangeCode = getExchange();
        Exchange exchange = connectorFactory.getTradingExchange(exchangeCode, symbol.getApiKey(),
                symbol.getSecretKey(), symbol.getPassword());
        GlobalTradeService tradeService = (GlobalTradeService) exchange.getTradeService();

        OpenOrdersParams params = OrderUtils.createOpenOrdersParams(exchangeCode, symbol);
        List<BitgetOrderInfoDto> orders = tradeService.getOpenOrdersNew(params);

        if (orders == null || orders.isEmpty()) {
            OpenOrdersBo openOrdersBo = new OpenOrdersBo(symbol.getSymbolId());
            setOpenOrders(exchangeCode, symbol, openOrdersBo);
            return;
        }

        OpenOrdersBo openOrdersBo = new OpenOrdersBo(symbol.getSymbolId());
        for (BitgetOrderInfoDto limitOrder : orders) {
            OrderBo orderBo = OrderUtils.convertToOrderBo(exchangeCode, limitOrder);
            if (orderBo != null) {
                orderBo.setSymbolId(symbol.getSymbolId());
                openOrdersBo.add(orderBo);
            }
        }
        setOpenOrders(exchangeCode, symbol, openOrdersBo);
    }

    /**
     * Returns open orders from in-memory cache without a REST call.
     * The cache is refreshed every 12 minutes by reloadOpenOrders().
     */
    public OpenOrdersBo getCachedOpenOrders(ExchangeCode exchangeCode, String symbolId) {
        return getOpenOrders(exchangeCode, symbolId);
    }

    public OpenOrdersBo getRemoteOpenOrders(SymbolBo symbol) {
        try {
            ExchangeCode exchangeCode = getExchange();
            Exchange exchange = connectorFactory.getTradingExchange(exchangeCode, symbol.getApiKey(),
                    symbol.getSecretKey(), symbol.getPassword());
            GlobalTradeService tradeService = (GlobalTradeService) exchange.getTradeService();

            OpenOrdersParams params = OrderUtils.createOpenOrdersParams(exchangeCode, symbol);
            List<BitgetOrderInfoDto> orders = tradeService.getOpenOrdersNew(params);

            if (orders == null || orders.isEmpty()) {
                OpenOrdersBo openOrdersBo = new OpenOrdersBo(symbol.getSymbolId());
                return openOrdersBo;
            }

            OpenOrdersBo openOrdersBo = new OpenOrdersBo(symbol.getSymbolId());
            for (BitgetOrderInfoDto limitOrder : orders) {
                OrderBo orderBo = OrderUtils.convertToOrderBo(exchangeCode, limitOrder);
                if (orderBo != null) {
                    orderBo.setSymbolId(symbol.getSymbolId());
                    openOrdersBo.addForNew(orderBo);
                }
            }
            return openOrdersBo;
        } catch (Exception e) {
            return getOpenOrders(getExchange(), symbol.getSymbolId());
        }
    }

    @Scheduled(initialDelay = 5*60*1000L, fixedDelay = 12*60*1000L)
    private void reloadOpenOrders() {
        List<SymbolBo> configs = symbolConfig.getActiveSymbols();
        if (CollUtil.isEmpty(configs)) {
            throw new IllegalStateException("No active symbol config found. Aborting initialization.");
        }

        for (SymbolBo symbolBo : configs) {
            ExchangeInfo exchangeInfo = exchangeConnectConfig.getExchangeInfo(getExchange().name());
            if (exchangeInfo == null || !exchangeInfo.getStreamPrivateUse()) {
                continue;
            }
            try {
                reProcessSymbolOrders(symbolBo);
            } catch (Exception e) {
                throw new IllegalStateException(
                        String.format("Failed to initialize open orders for symbol %s after 3 attempts.",
                                symbolBo.getSymbolId()), e);
            }
        }
    }

    private void reProcessSymbolOrders(SymbolBo symbol) throws IOException {
        Exchange exchange = connectorFactory.getTradingExchange(getExchange(), symbol.getApiKey(),
                symbol.getSecretKey(), symbol.getPassword());
        GlobalTradeService tradeService = (GlobalTradeService) exchange.getTradeService();

        OpenOrdersParams params = OrderUtils.createOpenOrdersParams(getExchange(), symbol);
        List<BitgetOrderInfoDto> orders = tradeService.getOpenOrdersNew(params);

        if (orders == null || orders.isEmpty()) {
            return;
        }

        OpenOrdersBo openOrdersBo = getOpenOrders(getExchange(), symbol.getSymbolId());
        for (BitgetOrderInfoDto limitOrder : orders) {
            OrderBo orderBo = OrderUtils.convertToOrderBo(getExchange(), limitOrder);
            if (orderBo != null) {
                orderBo.setSymbolId(symbol.getSymbolId());
                openOrdersBo.updateOpenOrder(orderBo);
            }
        }
    }
}
package com.orderbook.connector.global.service;

import com.orderbook.connector.common.dto.*;
import com.orderbook.connector.global.GlobalAdapters;
import com.orderbook.connector.global.GlobalErrorAdapter;
import com.orderbook.connector.global.GlobalExchange;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.knowm.xchange.binance.BinanceErrorAdapter;
import org.knowm.xchange.binance.dto.BinanceException;
import org.knowm.xchange.bitget.dto.BitgetException;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.trade.*;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class GlobalTradeService extends GlobalTradeServiceRaw implements TradeService {

    public GlobalTradeService(GlobalExchange exchange) {
        super(exchange);
    }

    @Override
    public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
        Validate.validState(orderQueryParams.length == 1);
        Validate.isInstanceOf(DefaultQueryOrderParam.class, orderQueryParams[0]);
        DefaultQueryOrderParam params = (DefaultQueryOrderParam) orderQueryParams[0];

        try {
            BitgetOrderInfoDto orderStatus = bitgetOrderInfoDto(params.getOrderId());
            return Collections.singletonList(GlobalAdapters.toOrder(orderStatus));
        } catch (BitgetException e) {
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
        try {
            List<UserTrade> userTradeList =
                    bitgetFills(params).stream()
                            .map(GlobalAdapters::toUserTrade)
                            .collect(Collectors.toList());
            return new UserTrades(userTradeList, TradeSortType.SortByID);
        } catch (BitgetException e) {
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
        try {
            return createOrder(GlobalAdapters.toBitgetPlaceOrderDto(marketOrder)).getOrderId();
        } catch (BitgetException e) {
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
        try {
            return createOrder(GlobalAdapters.toBitgetPlaceOrderDto(limitOrder)).getOrderId();
        } catch (BitgetException e) {
            log.error("placeLimitOrder limitOrder {} exception ", limitOrder, e);
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public boolean cancelOrder(CancelOrderParams cancelOrderParams) throws IOException {
        try {
            if (cancelOrderParams instanceof BitgetCancelOrderParam) {
                BitgetCancelOrderParam params = (BitgetCancelOrderParam) cancelOrderParams;
                String orderId = cancelOrder(GlobalAdapters.toBitgetCancelOrderDto(params)).getOrderId();
                return StringUtils.isNotBlank(orderId);
            } else {
                throw new RuntimeException("params error");
            }
        } catch (BitgetException e) {
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
        try {
            if (!(params instanceof BitgetOrderQueryParam)) {
                throw new ExchangeException("Parameters must be an instance of BitgetOrderQueryParam");
            }
            BitgetOrderQueryParam bitgetOrderQueryParam = (BitgetOrderQueryParam) params;
            List<BitgetOrderInfoDto> bitgetOrderInfoDtos = getOpenOrders(GlobalAdapters.toBitgetOrderQueryDto(bitgetOrderQueryParam));
            return GlobalAdapters.toOpenOrders(bitgetOrderInfoDtos);
        } catch (BinanceException e) {
            throw BinanceErrorAdapter.adapt(e);
        }
    }

    @Override
    public SpotModifyDepthVo changeDepth(SpotModifyDepthParam request) throws IOException {
        try {
            return super.changeDepth(request);
        } catch (BitgetException e) {
            log.error("changeDepth exception ", e);
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public SpotCancelOrderBySymbolResult cancelSymbolOrder(SpotCancelOrderBySymbolDto request) throws IOException {
        try {
            return super.cancelSymbolOrder(request);
        } catch (BitgetException e) {
            log.error("cancelSymbolOrder exception ", e);
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public SpotOrderBatchResult cancelBatchOrder(SpotCancelBatchOrderDTO request) throws IOException {
        try {
            return super.cancelBatchOrder(request);
        } catch (BitgetException e) {
            log.error("cancelBatchOrder exception ", e);
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public SpotOrderBatchResult placeBatchOrder(SpotBatchOrdersDto request) throws IOException {
        try {
            return super.placeBatchOrder(request);
        } catch (BitgetException e) {
            log.error("placeBatchOrder exception ", e);
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    public List<BitgetOrderInfoDto> getOpenOrdersNew(OpenOrdersParams params) throws IOException {
        try {
            if (!(params instanceof BitgetOrderQueryParam)) {
                throw new ExchangeException("Parameters must be an instance of BitgetOrderQueryParam");
            }
            BitgetOrderQueryParam bitgetOrderQueryParam = (BitgetOrderQueryParam) params;
            return super.getOpenOrders(GlobalAdapters.toBitgetOrderQueryDto(bitgetOrderQueryParam));
        } catch (BinanceException e) {
            throw BinanceErrorAdapter.adapt(e);
        }
    }

    @Override
    public List<SpotFillsOrderResult> getTradeHistoryV1(SpotFillsOrderDTO spotFillsOrderDTO) throws IOException {
        try {
            return bitgetFillsV1(spotFillsOrderDTO);
        } catch (BitgetException e) {
            throw GlobalErrorAdapter.adapt(e);
        }
    }
}
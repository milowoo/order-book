package com.orderbook.connector.bitget.service;

import com.orderbook.connector.bitget.BitgetAdapters;
import com.orderbook.connector.bitget.BitgetErrorAdapter;
import com.orderbook.connector.bitget.BitgetExchange;
import com.orderbook.connector.common.dto.BitgetCancelOrderParam;
import com.orderbook.connector.common.dto.BitgetOrderQueryParam;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.knowm.xchange.bitget.dto.BitgetException;
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

public class BitgetTradeService extends BitgetTradeServiceRaw implements TradeService {

    public BitgetTradeService(BitgetExchange exchange) {
        super(exchange);
    }

    @Override
    public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
        Validate.validState(orderQueryParams.length == 1);
        Validate.isInstanceOf(DefaultQueryOrderParam.class, orderQueryParams[0]);
        DefaultQueryOrderParam params = (DefaultQueryOrderParam) orderQueryParams[0];

        try {
            BitgetOrderInfoDto orderStatus = bitgetOrderInfoDto(params.getOrderId());
            return Collections.singletonList(BitgetAdapters.toOrder(orderStatus));
        } catch (BitgetException e) {
            throw BitgetErrorAdapter.adapt(e);
        }
    }

    @Override
    public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
        try {
            List<UserTrade> userTradeList =
                    bitgetFills(params).stream()
                            .map(BitgetAdapters::toUserTrade)
                            .collect(Collectors.toList());

            return new UserTrades(userTradeList, TradeSortType.SortByID);
        } catch (BitgetException e) {
            throw BitgetErrorAdapter.adapt(e);
        }
    }

    @Override
    public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
        try {
            return createOrder(BitgetAdapters.toBitgetPlaceOrderDto(marketOrder)).getOrderId();
        } catch (BitgetException e) {
            throw BitgetErrorAdapter.adapt(e);
        }
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
        try {
            return createOrder(BitgetAdapters.toBitgetPlaceOrderDto(limitOrder)).getOrderId();
        } catch (BitgetException e) {
            throw BitgetErrorAdapter.adapt(e);
        }
    }

    @Override
    public boolean cancelOrder(CancelOrderParams cancelOrderParams) throws IOException {
        try {
            if (cancelOrderParams instanceof BitgetCancelOrderParam) {
                BitgetCancelOrderParam params = (BitgetCancelOrderParam) cancelOrderParams;
                String orderId = cancelOrder(BitgetAdapters.toBitgetCancelOrderDto(params)).getOrderId();
                return StringUtils.isNotBlank(orderId);
            } else {
                throw new RuntimeException("params error");
            }
        } catch (BitgetException e) {
            throw BitgetErrorAdapter.adapt(e);
        }
    }

    /**
     * 查询在途订单
     */
    @Override
    public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
        try {
            if (!(params instanceof BitgetOrderQueryParam)) {
                throw new ExchangeException(
                        "Parameters must be an instance of BitgetOrderQueryParam");
            }

            BitgetOrderQueryParam bitgetOrderQueryParam = (BitgetOrderQueryParam) params;
            List<BitgetOrderInfoDto> bitgetOrderInfoDtos = getOpenOrders(BitgetAdapters.toBitgetOrderQueryDto(bitgetOrderQueryParam));

            return BitgetAdapters.toOpenOrders(bitgetOrderInfoDtos);
        } catch (BitgetException e) {
            throw BitgetErrorAdapter.adapt(e);
        }
    }
}
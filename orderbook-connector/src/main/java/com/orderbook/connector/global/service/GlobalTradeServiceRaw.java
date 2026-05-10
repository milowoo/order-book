package com.orderbook.connector.global.service;

import com.orderbook.connector.common.dto.*;
import com.orderbook.connector.global.GlobalAdapters;
import com.orderbook.connector.global.GlobalExchange;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.bitget.dto.trade.BitgetFillDto;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto;
import org.knowm.xchange.service.trade.params.*;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

import java.io.IOException;
import java.util.List;

public abstract class GlobalTradeServiceRaw extends GlobalBaseService {

    public GlobalTradeServiceRaw(GlobalExchange exchange) {
        super(exchange);
    }

    public List<BitgetFillDto> bitgetFills(TradeHistoryParams params) throws IOException {
        // get arguments
        Instrument instrument =
                params instanceof TradeHistoryParamInstrument
                        ? ((TradeHistoryParamInstrument) params).getInstrument()
                        : null;
        Integer limit =
                params instanceof TradeHistoryParamLimit
                        ? ((TradeHistoryParamLimit) params).getLimit()
                        : null;
        String orderId =
                params instanceof TradeHistoryParamOrderId
                        ? ((TradeHistoryParamOrderId) params).getOrderId()
                        : null;
        String lastTradeId =
                params instanceof TradeHistoryParamsIdSpan
                        ? ((TradeHistoryParamsIdSpan) params).getEndId()
                        : null;
        Long from = null;
        Long to = null;
        if (params instanceof TradeHistoryParamsTimeSpan) {
            TradeHistoryParamsTimeSpan paramsTimeSpan = ((TradeHistoryParamsTimeSpan) params);
            from = paramsTimeSpan.getStartTime() != null ? paramsTimeSpan.getStartTime().getTime() : null;
            to = paramsTimeSpan.getEndTime() != null ? paramsTimeSpan.getEndTime().getTime() : null;
        }

        return bitgetAuthenticated
                .fills(
                        apiKey,
                        bitgetDigest,
                        passphrase,
                        exchange.getNonceFactory(),
                        paptrading,
                        GlobalAdapters.toString(instrument),
                        limit,
                        orderId,
                        from,
                        to,
                        lastTradeId)
                .getData();
    }

    public BitgetOrderInfoDto bitgetOrderInfoDto(String orderId) throws IOException {
        List<BitgetOrderInfoDto> results =
                bitgetAuthenticated
                        .orderInfo(apiKey, bitgetDigest, passphrase, exchange.getNonceFactory(), paptrading, orderId)
                        .getData();
        if (results.size() != 1) {
            return null;
        }
        return results.get(0);
    }

    public BitgetOrderInfoDto createOrder(BitgetPlaceOrderDto bitgetPlaceOrderDto)
            throws IOException {
        return bitgetAuthenticated
                .createOrder(
                        apiKey, bitgetDigest, passphrase, exchange.getNonceFactory(), paptrading,
                        bitgetPlaceOrderDto)
                .getData();
    }

    public BitgetOrderInfoDto cancelOrder(BitgetCancelOrderDto bitgetCancelOrderDto)
            throws IOException {
        return bitgetAuthenticated
                .cancelOrder(
                        apiKey, bitgetDigest, passphrase, exchange.getNonceFactory(), paptrading,
                        bitgetCancelOrderDto)
                .getData();
    }

    public List<BitgetOrderInfoDto> getOpenOrders(BitgetOrderQueryDto orderQueryDto)
            throws IOException {
        return bitgetAuthenticated
                .getOpenOrders(
                        apiKey, bitgetDigest, passphrase, exchange.getNonceFactory(), paptrading,
                        orderQueryDto.getSymbol(),
                        orderQueryDto.getLimit(),
                        orderQueryDto.getOrderId(),
                        orderQueryDto.getStartTime(),
                        orderQueryDto.getEndTime(),
                        orderQueryDto.getIdLessThan(),
                        orderQueryDto.getTpslType(),
                        orderQueryDto.getRequestTime(),
                        orderQueryDto.getReceiveWindow())
                .getData();
    }

    public SpotModifyDepthVo changeDepth(SpotModifyDepthParam request) throws IOException {
        return bitgetAuthenticated
                .changeDepth(
                        apiKey, bitgetDigest, passphrase, exchange.getNonceFactory(), paptrading, request)
                .getData();
    }

    public SpotCancelOrderBySymbolResult cancelSymbolOrder(SpotCancelOrderBySymbolDto request)
            throws IOException {
        return bitgetAuthenticated
                .cancelSymbolOrder(
                        apiKey, bitgetDigest, passphrase, exchange.getNonceFactory(), paptrading, request)
                .getData();
    }

    public SpotOrderBatchResult cancelBatchOrder(SpotCancelBatchOrderDTO request)
            throws IOException {
        return bitgetAuthenticated
                .cancelBatchOrder(
                        apiKey, bitgetDigest, passphrase, exchange.getNonceFactory(), paptrading, request)
                .getData();
    }

    public SpotOrderBatchResult placeBatchOrder(SpotBatchOrdersDto request)
            throws IOException {
        return bitgetAuthenticated
                .batchOrders(
                        apiKey, bitgetDigest, passphrase, exchange.getNonceFactory(), paptrading, request)
                .getData();
    }

    public List<SpotFillsOrderResult> bitgetFillsV1(SpotFillsOrderDTO request)
            throws IOException {
        return bitgetAuthenticated
                .fillsV1(
                        apiKey, bitgetDigest, passphrase, exchange.getNonceFactory(), paptrading, request)
                .getData();
    }

    public abstract List<BitgetOrderInfoDto> getOpenOrdersNew(OpenOrdersParams params)
            throws IOException;

    public abstract List<SpotFillsOrderResult> getTradeHistoryV1(SpotFillsOrderDTO spotFillsOrderDTO)
            throws IOException;
}
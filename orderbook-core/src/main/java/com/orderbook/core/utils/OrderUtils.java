package com.orderbook.core.utils;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.common.dto.BitgetCancelOrderParam;
import com.orderbook.connector.common.dto.BitgetOrderQueryParam;
import com.orderbook.core.domain.OrderBo;
import com.orderbook.core.domain.SymbolBo;
import org.knowm.xchange.dto.Order.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.binance.dto.trade.BinanceCancelOrderParams;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.trade.BybitCancelOrderParams;
import org.knowm.xchange.bybit.dto.trade.BybitOpenOrdersParam;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

import java.math.BigDecimal;

@Slf4j
public class OrderUtils {

    public static OpenOrdersParams createOpenOrdersParams(ExchangeCode exchange, SymbolBo symbolBo) {
        String exchangeName = exchange.name();
        CurrencyPair currencyPair = SymbolUtils.fromSymbol(symbolBo);
        if ("Bybit".equalsIgnoreCase(exchangeName)) {
            Instrument instrument = new CurrencyPair(symbolBo.getSymbolId()); // 现货市场常用交易对
            return new BybitOpenOrdersParam(instrument, BybitCategory.SPOT);
        } else if ("Binance".equalsIgnoreCase(exchangeName)) {
            return new DefaultOpenOrdersParamCurrencyPair(currencyPair);
        } else if ("BitGet".equalsIgnoreCase(exchangeName)) {
            return new BitgetOrderQueryParam(currencyPair);
        } else if ("OSL_ID".equalsIgnoreCase(exchangeName)) {
            return new BitgetOrderQueryParam(currencyPair);
        } else if ("OSL_GLOBAL".equalsIgnoreCase(exchangeName)) {
            return new BitgetOrderQueryParam(currencyPair);
        } else {
            throw new UnsupportedOperationException("Unsupported exchange: " + exchangeName);
        }
    }

    public static OrderBo convertToOrderBo(ExchangeCode exchangeCode, BitgetOrderInfoDto order) {
        try {
            OrderBo orderBo = new OrderBo();
            orderBo.setSymbolId(order.getSymbol());
            BigDecimal price = order.getPriceAvg();
            orderBo.setOrderId(Long.valueOf(order.getOrderId()));
            orderBo.setClientOrderId(order.getClientOid());
            orderBo.setOrderStatus(convertOrderStatus(order.getOrderStatus()));
            orderBo.setPrice(price);
            orderBo.setQuantity(order.getSize());
            orderBo.setAmount(order.getSize().multiply(price));
            BigDecimal remainingQty = order.getSize().subtract(order.getBaseVolume());
            orderBo.setRemainingQty(remainingQty);
            orderBo.setRemainingAmount(remainingQty.multiply(price));

            String side = "buy";
            if ("ask".equalsIgnoreCase(order.getOrderSide().name()) ||
                    "EXIT_ASK".equalsIgnoreCase(order.getOrderSide().name())) {
                side = "sell";
            }
            orderBo.setSide(side);
            orderBo.setCreateTime(order.getCreatedAt().getEpochSecond());

            return orderBo;
        } catch (Exception e) {
            log.error("Failed to convert order to exchangeCode {} OrderBo {}", exchangeCode.name(), order, e);
            return null;
        }
    }

    private static String convertOrderStatus(BitgetOrderInfoDto.BitgetOrderStatus orderStatus) {
        if (orderStatus.equals(BitgetOrderInfoDto.BitgetOrderStatus.CANCELLED)) {
            return OrderStatus.CANCELED.name();
        }
        if (orderStatus.equals(BitgetOrderInfoDto.BitgetOrderStatus.FILLED)) {
            return OrderStatus.FILLED.name();
        }
        if (orderStatus.equals(BitgetOrderInfoDto.BitgetOrderStatus.PARTIALLY_FILLED)) {
            return OrderStatus.PARTIALLY_FILLED.name();
        }
        return OrderStatus.NEW.name();
    }

    public static CancelOrderParams createCancelOrderParams(ExchangeCode exchange, CurrencyPair pair, String orderId) {
        String exchangeName = exchange.name();
        if ("Bybit".equalsIgnoreCase(exchangeName)) {
            return new BybitCancelOrderParams(pair, orderId, "userReference");
        } else if ("Binance".equalsIgnoreCase(exchangeName)) {
            return new BinanceCancelOrderParams(pair, orderId);
        } else if ("BitGet".equalsIgnoreCase(exchangeName)) {
            return new BitgetCancelOrderParam(pair, orderId);
        } else if ("OSL_ID".equalsIgnoreCase(exchangeName)) {
            return new BitgetCancelOrderParam(pair, orderId);
        } else if ("OSL_GLOBAL".equalsIgnoreCase(exchangeName)) {
            return new BitgetCancelOrderParam(pair, orderId);
        } else {
            throw new UnsupportedOperationException("Unsupported exchange: " + exchangeName);
        }
    }
}
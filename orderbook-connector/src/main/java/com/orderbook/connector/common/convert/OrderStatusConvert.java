package com.orderbook.connector.common.convert;

import com.orderbook.cmd.ExchangeCode;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto;
import org.knowm.xchange.dto.Order;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class OrderStatusConvert {

    public static Order.OrderStatus convert(String status, ExchangeCode exchangeCode) {
        return CONVERT_FUNCTION_MAP.get(exchangeCode).apply(status);
    }

    /**
     * 参数转换
     */
    private final static Map<ExchangeCode, Function<String, Order.OrderStatus>> CONVERT_FUNCTION_MAP = new HashMap<>() {{
        // bitget
        put(ExchangeCode.BITGET, status -> {
            BitgetOrderInfoDto.BitgetOrderStatus bitgetOrderStatus = BitgetOrderInfoDto.BitgetOrderStatus.valueOf(status);
            switch (bitgetOrderStatus) {
                case PENDING:
                    return Order.OrderStatus.OPEN;
                case PARTIALLY_FILLED:
                    return Order.OrderStatus.PARTIALLY_FILLED;
                case FILLED:
                    return Order.OrderStatus.FILLED;
                case CANCELLED:
                    return Order.OrderStatus.CANCELED;
                default:
                    return Order.OrderStatus.UNKNOWN;
            }
        });

        // OSL-IO
        put(ExchangeCode.OSL_IO, status -> {
            BitgetOrderInfoDto.BitgetOrderStatus bitgetOrderStatus = BitgetOrderInfoDto.BitgetOrderStatus.valueOf(status);
            switch (bitgetOrderStatus) {
                case PENDING:
                    return Order.OrderStatus.OPEN;
                case PARTIALLY_FILLED:
                    return Order.OrderStatus.PARTIALLY_FILLED;
                case FILLED:
                    return Order.OrderStatus.FILLED;
                case CANCELLED:
                    return Order.OrderStatus.CANCELED;
                default:
                    return Order.OrderStatus.UNKNOWN;
            }
        });
    }};
}
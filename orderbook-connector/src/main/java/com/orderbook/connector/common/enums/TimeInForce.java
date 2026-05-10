package com.orderbook.connector.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.common.dto.BitgetPlaceOrderDto;
import org.knowm.xchange.dto.Order;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public enum TimeInForce implements Order.IOrderFlags {
    // 普通限价单，一直有效直至取消
    GTC,
    // 只做 maker 订单
    POST_ONLY,
    // 全部成交或立即取消
    FOK,
    // 立即成交并取消剩余
    IOC;

    /**
     * 通过枚举名称获取指定枚举
     * Params:
     * name
     * Returns:
     */
    @JsonCreator
    public static TimeInForce getTimeInForce(String name) {
        try {
            return valueOf(name);
        } catch (Exception var2) {
            throw new RuntimeException("Unknown ordtime in force " + name + ".");
        }
    }

    /**
     * 通过枚举名称获取指定枚举
     * Returns:
     */
    @JsonCreator
    public Order.IOrderFlags convert(ExchangeCode exchange) {
        try {
            return CONVERT_FUNCTION_MAP.get(exchange).apply(this);
        } catch (Exception var2) {
            throw new RuntimeException("Unknown ordtime in force " + this.name() + ".");
        }
    }

    /**
     * 参数转换
     */
    private final static Map<ExchangeCode, Function<TimeInForce, Order.IOrderFlags>> CONVERT_FUNCTION_MAP = new HashMap<>() {{
        // bitget
        put(ExchangeCode.BITGET, timeInForce -> {
            switch (timeInForce) {
                case GTC:
                    return BitgetPlaceOrderDto.TimeInForce.GOOD_TIL_CANCELLED;
                case POST_ONLY:
                    return BitgetPlaceOrderDto.TimeInForce.POST_ONLY;
                case FOK:
                    return BitgetPlaceOrderDto.TimeInForce.FILL_OR_KILL;
                case IOC:
                    return BitgetPlaceOrderDto.TimeInForce.IMMEDIATE_OR_CANCEL;
                default:
                    return null;
            }
        });

        // osl_id
        put(ExchangeCode.OSL_IO, timeInForce -> {
            switch (timeInForce) {
                case GTC:
                    return BitgetPlaceOrderDto.TimeInForce.GOOD_TIL_CANCELLED;
                case POST_ONLY:
                    return BitgetPlaceOrderDto.TimeInForce.POST_ONLY;
                case FOK:
                    return BitgetPlaceOrderDto.TimeInForce.FILL_OR_KILL;
                case IOC:
                    return BitgetPlaceOrderDto.TimeInForce.IMMEDIATE_OR_CANCEL;
                default:
                    return null;
            }
        });
    }};
}
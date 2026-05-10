package com.orderbook.cmd;

import lombok.Getter;
import java.util.List;

@Getter
public enum ExchangeCode {
    BINANCE,
    BITGET,
    BYBIT,
    OSL_GLOBAL,
    OSL_IO,
    OSL_GLOBAL_V2;

    /**
     * 获取bitget系平台
     * @return
     */
    public static List<ExchangeCode> bitgetIn() {
        return List.of(BITGET, OSL_GLOBAL, OSL_IO);
    }
}
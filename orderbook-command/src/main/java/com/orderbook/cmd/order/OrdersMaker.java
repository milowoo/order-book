package com.orderbook.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.util.Map;


public interface OrdersMaker extends StrategyCmd {

    /**
     * 铺单 orders_maker(exchange, symbol)
     * Params:
     *      env
     *      exchangeCode
     *      symbol
     * Returns:
     */
    @Override
    Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);
}
package com.orderbook.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.util.Map;


public interface CancelOrderBookOutOrder extends StrategyCmd {

    /**
     * 取消某个symbol的订单簿外的订单（只保留level档位）cancel_order(symbol)
     * Params:
     *      env
     *      exchangeCode
     *      symbol
     * Returns:
     */
    @Override
    Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);
}
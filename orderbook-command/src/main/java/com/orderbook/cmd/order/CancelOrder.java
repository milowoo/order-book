package com.orderbook.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.util.Map;

public interface CancelOrder extends StrategyCmd {

    /**
     * 取消某个symbol的订单 cancel_order(symbol)
     * Params:
     *      env
     *      exchangeCode
     *      symbol
     * Returns:
     */
    @Override
    Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);

    /**
     * 取消某个symbol的订单 cancel_order(symbol, orderId)
     * Params:
     *      env
     *      exchangeCode
     *      symbol
     *      orderId
     * Returns:
     */
    @Override
    Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String orderId);
}
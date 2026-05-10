package com.orderbook.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.util.Map;


public interface CancelOrderBySide extends StrategyCmd {

    /**
     * 取消某个symbol的订单 cancel_order(symbol, side)
     * Params:
     *      env
     *      exchangeCode
     *      symbol
     *      side
     * Returns:
     */
    @Override
    Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side);
}
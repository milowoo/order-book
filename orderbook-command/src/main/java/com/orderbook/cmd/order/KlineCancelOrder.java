package com.orderbook.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.util.Map;

public interface KlineCancelOrder extends StrategyCmd {

    /**
     * 取消某个orderId的订单 cancel_order(symbol, orderId)
     * @param env
     * @param exchangeCode
     * @param symbol
     * @param orderId
     * @return
     */
    @Override
    Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String orderId);

    /**
     * 取消某个symbol的订单 cancel_order(symbol)
     * @param env
     * @param exchangeCode
     * @param symbol
     * @return
     */
    @Override
    Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);
}
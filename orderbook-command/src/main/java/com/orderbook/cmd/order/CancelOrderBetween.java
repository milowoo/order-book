package com.orderbook.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.util.Map;


public interface CancelOrderBetween extends StrategyCmd {

    /**
     * 取消某个价格区间的所有订单 cancel_order_between(symbol,side,beginPrice,endPrice)
     * Params:
     *      env
     *      exchangeCode
     *      symbol
     *      side
     *      beginPrice
     *      endPrice
     * Returns:
     */
    @Override
    Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String beginPrice, String endPrice);
}
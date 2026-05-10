package com.orderbook.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.util.Map;


public interface KlineMaker extends StrategyCmd {

    /**
     * 针对某个symbol进行kline画线
     * Params:
     *      env
     *      exchangeCode
     *      symbol
     * Returns:
     */
    @Override
    Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);
}
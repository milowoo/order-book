package com.orderbook.cmd.price;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.util.Map;


public interface LastPriceUpdateTime extends StrategyCmd {

    /**
     * 最新成交时间 last_price(exchange,symbol)
     */
    @Override
    Long call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);
}
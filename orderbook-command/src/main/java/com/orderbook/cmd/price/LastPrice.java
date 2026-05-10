package com.orderbook.cmd.price;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.math.BigDecimal;
import java.util.Map;


public interface LastPrice extends StrategyCmd {

    /**
     * 最新价格 last_price(exchange,symbol)
     */
    @Override
    BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);
}
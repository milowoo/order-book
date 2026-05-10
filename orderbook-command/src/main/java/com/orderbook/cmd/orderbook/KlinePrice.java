package com.orderbook.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.math.BigDecimal;
import java.util.Map;

public interface KlinePrice extends StrategyCmd {

    /**
     * 价格level price_level(exchange,symbol,side,level)
     */
    @Override
    BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String level);
}
package com.orderbook.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.math.BigDecimal;
import java.util.Map;

public interface NotionalRange extends StrategyCmd {

    /**
     * 价格区间内订单notional notional_range(exchange,symbol,side,beginPrice,endPrice)
     */
    @Override
    BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String beginPrice, String endPrice);
}
package com.orderbook.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.math.BigDecimal;
import java.util.Map;


public interface NotionalNoBot extends StrategyCmd {

    /**
     * 最优价格 notional_no_bot(exchange,symbol,side,beginPrice,endPrice)
     */
    @Override
    BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String beginPrice, String endPrice);
}
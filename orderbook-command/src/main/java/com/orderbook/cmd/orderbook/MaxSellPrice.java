package com.orderbook.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.math.BigDecimal;
import java.util.Map;

public interface MaxSellPrice extends StrategyCmd {

    /**
     * 订单簿最大卖价 max_sell_price(exchange,symbol)
     */
    @Override
    BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);
}
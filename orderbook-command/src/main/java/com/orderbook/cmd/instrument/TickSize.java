package com.orderbook.cmd.instrument;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.math.BigDecimal;
import java.util.Map;

/**
 * symbol下单精度 tick_size(exchange,symbol)
 */
public interface TickSize extends StrategyCmd {

    @Override
    BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);
}
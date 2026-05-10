package com.orderbook.cmd.instrument;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 最小下单数量 min_size(exchange,symbol)
 */
public interface MinSize extends StrategyCmd {

    @Override
    BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);
}
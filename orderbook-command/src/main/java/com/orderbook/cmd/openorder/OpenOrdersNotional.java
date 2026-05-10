package com.orderbook.cmd.openorder;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 当前账户挂单notional open_order_notional(exchange,symbol,side)
 */
public interface OpenOrdersNotional extends StrategyCmd {

    @Override
    BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side);

    /**
     * 当前账户挂单notional open_orders_notional(exchange,symbol,side,beginPrice,endPrice)
     */
    @Override
    BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String beginPrice, String endPrice);
}
package com.orderbook.cmd.balance;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 账户可用余额 avail_balance(exchange,ccy)
 */
public interface AvailBalance extends StrategyCmd {

    @Override
    BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String ccy);
}
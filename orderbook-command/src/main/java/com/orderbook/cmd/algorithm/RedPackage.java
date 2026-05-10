package com.orderbook.cmd.algorithm;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.util.List;
import java.util.Map;

/**
 * read_package(money,num)
 */
public interface RedPackage extends StrategyCmd {

    @Override
    List<Long> call(Map<String, Object> env, ExchangeCode exchangeCode, String money, String num);
}
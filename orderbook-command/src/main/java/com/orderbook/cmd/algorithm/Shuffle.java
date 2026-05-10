package com.orderbook.cmd.algorithm;

import com.orderbook.cmd.StrategyCmd;

import java.util.List;
import java.util.Map;

/**
 * read_package(money,num)
 */
public interface Shuffle extends StrategyCmd {

    @Override
    List<Object> call(Map<String, Object> env, List<Object> params);
}
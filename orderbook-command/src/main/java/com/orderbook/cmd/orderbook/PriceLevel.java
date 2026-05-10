package com.orderbook.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;


public interface PriceLevel extends StrategyCmd {

    /**
     * 价格level price_level(exchange,symbol,side,beginPrice,endPrice)
     */
    @Override
    List<BigDecimal> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String beginPrice, String endPrice);

    /**
     * 价格level price_level(exchange,symbol,side)
     */
    @Override
    List<BigDecimal> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side);
}
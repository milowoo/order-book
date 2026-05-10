package com.orderbook.cmd.openorder;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;
import com.orderbook.cmd.dto.OrderDto;

import java.util.List;
import java.util.Map;

/**
 * 当前账户挂单对象 open_orders_obj(exchange,symbol)
 */
public interface OpenOrdersObj extends StrategyCmd {

    @Override
    List<OrderDto> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol);

    /**
     * 当前账户挂单对象 open_orders_obj(exchange,symbol,side)
     */
    @Override
    List<OrderDto> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side);

    /**
     * 当前账户挂单对象 open_orders_obj(exchange,symbol,side,price)
     */
    @Override
    List<OrderDto> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String price);

    /**
     * 当前账户挂单对象 open_orders_obj(exchange,symbol,side,beginPrice,endPrice)
     */
    @Override
    List<OrderDto> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String beginPrice, String endPrice);
}
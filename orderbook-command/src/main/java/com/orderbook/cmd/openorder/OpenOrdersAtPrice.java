package com.orderbook.cmd.openorder;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.StrategyCmd;
import com.orderbook.cmd.dto.OrderDto;

import java.util.List;
import java.util.Map;

/**
 * 当前账户在某价格的open订单 open_orders_at(exchange,symbol,side,price)
 */
public interface OpenOrdersAtPrice extends StrategyCmd {

    @Override
    List<OrderDto> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String price);
}
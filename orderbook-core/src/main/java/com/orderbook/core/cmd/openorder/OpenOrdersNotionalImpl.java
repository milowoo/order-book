package com.orderbook.core.cmd.openorder;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.openorder.OpenOrdersNotional;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.OrderBo;
import com.orderbook.core.domain.SymbolBo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.Optional.ofNullable;

@Slf4j
@Command(name = ExchangeFunc.OPEN_ORDERS_NOTIONAL)
@RequiredArgsConstructor
public class OpenOrdersNotionalImpl extends AbstractOpenOrdersCmd implements OpenOrdersNotional {

    // 当前账户挂单notional open_order_notional(exchange,symbol,side)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side) {
        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("OpenOrdersNotionalImpl call begin exchangeCode {} symbol={} side={}", exchangeCode.name(), symbol, side);
        }

        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }

        List<OrderBo> orderBos = getAllBotOpenOrders(env, exchangeCode, symbolBo.getSymbolId());
        if (orderBos == null) {
            log.info("OpenOrdersNotionalImpl no open order exchangeCode {} symbol={} side={}", exchangeCode.name(), symbol, side);
            return BigDecimal.ZERO;
        }

        // 根据方向确定过滤条件
        Predicate<OrderBo> sideFilter = "BUY".equalsIgnoreCase(side)
                ? OrderBo::isBuyOrder
                : OrderBo::isSellOrder;

        BigDecimal notional = orderBos.stream()
                .filter(sideFilter)
                .map(order -> ofNullable(order.notional()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("OpenOrdersNotionalImpl exchange {} symbol={} side={} notional {}", exchangeCode.name(), symbol, side, notional);
        }

        return notional;
    }

    // 当前账户挂单notional open_orders_notional(exchange,symbol,side,beginPrice,endPrice)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String beginPriceStr, String endPriceStr) {
        log.info("OpenOrdersNotionalImpl begin exchange {} symbol={} side={} beginPrice {} endPrice {}",
                exchangeCode.name(), symbol, side, beginPriceStr, endPriceStr);

        List<OrderBo> orderBos = getAllBotOpenOrders(env, exchangeCode, symbol);
        if (orderBos == null) {
            log.info("OpenOrdersNotionalImpl no open orders exchange {} symbol={} side={} beginPrice {} endPrice {}",
                    exchangeCode.name(), symbol, side, beginPriceStr, endPriceStr);
            return BigDecimal.ZERO;
        }

        // 将字符串价格转为 BigDecimal
        BigDecimal beginPrice = parseBigDecimal(beginPriceStr, BigDecimal.ZERO);
        BigDecimal endPrice = parseBigDecimal(endPriceStr, BigDecimal.ZERO);

        // 根据方向确定过滤条件
        Predicate<OrderBo> sideFilter = "BUY".equalsIgnoreCase(side)
                ? OrderBo::isBuyOrder
                : OrderBo::isSellOrder;

        BigDecimal notional = orderBos.stream()
                .filter(sideFilter)
                .filter(order -> isWithinPriceRange(order.getPrice(), beginPrice, endPrice))
                .map(order -> ofNullable(order.notional()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("OpenOrdersNotionalImpl end exchange {} symbol={} side={} beginPrice {} endPrice {} notional {}",
                exchangeCode.name(), symbol, side, beginPriceStr, endPriceStr, notional);

        return notional;
    }
}
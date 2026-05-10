package com.orderbook.core.cmd.openorder;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.dto.OrderDto;
import com.orderbook.cmd.openorder.OpenOrdersObj;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.OrderBo;
import com.orderbook.core.domain.SymbolBo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Command(name = ExchangeFunc.OPEN_ORDERS_OBJ)
@RequiredArgsConstructor
public class OpenOrdersObjImpl extends AbstractOpenOrdersCmd implements OpenOrdersObj {

    // 当前账户挂单对象 open_orders_obj(exchange,symbol)
    @Override
    public List<OrderDto> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("OpenOrdersObjImpl begin exchange {}, symbol {}", exchangeCode.name(), symbol);
        }

        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return Collections.emptyList();
        }

        List<OrderBo> orderBos = getAllBotOpenOrders(env, exchangeCode, symbolBo.getSymbolId());
        if (orderBos == null) {
            log.warn("OpenOrdersObjImpl no order exchange {}, symbol {}", exchangeCode, symbol);
            return Collections.emptyList();
        }

        List<OrderDto> result = orderBos.stream()
                .map(this::convertToDto)
                .sorted(Comparator.comparing(OrderDto::getPrice, Comparator.nullsFirst(BigDecimal::compareTo)))
                .collect(Collectors.toList());

        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("OpenOrdersObjImpl end exchange {}, symbol {} result {}", exchangeCode, symbol, result);
        }

        return result;
    }

    // 当前账户挂单对象 open_orders_obj(exchange,symbol,side)
    @Override
    public List<OrderDto> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side) {
        log.info("OpenOrdersObjImpl begin exchange {}, symbol {} side {}", exchangeCode.name(), symbol, side);
        List<OrderBo> orderBos = getAllBotOpenOrders(env, exchangeCode, symbol);
        if (orderBos == null) {
            log.info("OpenOrdersObjImpl no order exchange {}, symbol {} side {}", exchangeCode, symbol, side);
            return Collections.emptyList();
        }

        // 根据 side 确定过滤器
        Predicate<OrderBo> sideFilter = "BUY".equalsIgnoreCase(side)
                ? OrderBo::isBuyOrder
                : OrderBo::isSellOrder;

        List<OrderDto> result = orderBos.stream()
                .filter(sideFilter)
                .map(this::convertToDto)
                .sorted(Comparator.comparing(OrderDto::getPrice, Comparator.nullsFirst(BigDecimal::compareTo)))
                .collect(Collectors.toList());

        log.info("OpenOrdersObjImpl end exchange {}, symbol {} side {} result {}",
                exchangeCode, symbol, side, result);

        return result;
    }

    // 当前账户挂单对象 open_orders_obj(exchange,symbol,side,price)
    @Override
    public List<OrderDto> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String priceStr) {
        log.info("OpenOrdersObjImpl begin exchange {}, symbol {} side {} price {}",
                exchangeCode.name(), symbol, side, priceStr);
        List<OrderBo> orderBos = getAllBotOpenOrders(env, exchangeCode, symbol);
        if (orderBos == null || orderBos.isEmpty()) {
            log.info("OpenOrdersObjImpl no orders exchange {}, symbol {} side {} price {}",
                    exchangeCode, symbol, side, priceStr);
            return Collections.emptyList();
        }

        BigDecimal price = parseBigDecimal(priceStr, BigDecimal.ZERO);
        // 根据 side 确定过滤器
        Predicate<OrderBo> sideFilter = "BUY".equalsIgnoreCase(side)
                ? OrderBo::isBuyOrder
                : OrderBo::isSellOrder;

        // 执行流操作
        List<OrderDto> result = orderBos.stream()
                .filter(sideFilter)
                .filter(order -> isEqualPrice(order.getPrice(), price))
                .map(this::convertToDto)
                .sorted(Comparator.comparing(OrderDto::getPrice, Comparator.nullsFirst(BigDecimal::compareTo)))
                .collect(Collectors.toList());

        log.info("OpenOrdersObjImpl end exchange {}, symbol {} side {} price {} result {}",
                exchangeCode, symbol, side, priceStr, result);

        return result;
    }

    private static boolean isEqualPrice(BigDecimal price, BigDecimal filterPrice) {
        if (price == null || filterPrice == null) return false;
        return price.compareTo(filterPrice) == 0;
    }

    // 当前账户挂单对象 open_orders_obj(account,symbol,side,beginPrice,endPrice)
    @Override
    public List<OrderDto> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol,
                               String side, String beginPriceStr, String endPriceStr) {
        log.info("OpenOrdersObjImpl begin exchange {}, symbol {} side {} beginPrice {} endPrice {}",
                exchangeCode.name(), symbol, side, beginPriceStr, endPriceStr);
        List<OrderBo> orderBos = getAllBotOpenOrders(env, exchangeCode, symbol);
        if (orderBos == null || orderBos.isEmpty()) {
            log.info("OpenOrdersObjImpl no order exchange {}, symbol {} side {} beginPrice {} endPrice {}",
                    exchangeCode, symbol, side, beginPriceStr, endPriceStr);
            return Collections.emptyList();
        }

        // 将字符串价格转为 BigDecimal
        BigDecimal beginPrice = parseBigDecimal(beginPriceStr, BigDecimal.ZERO);
        BigDecimal endPrice = parseBigDecimal(endPriceStr, BigDecimal.ZERO);

        // 根据 side 确定过滤器
        Predicate<OrderBo> sideFilter = "BUY".equalsIgnoreCase(side)
                ? OrderBo::isBuyOrder
                : OrderBo::isSellOrder;

        // 执行流操作
        List<OrderDto> result = orderBos.stream()
                .filter(sideFilter)
                .filter(order -> isWithinPriceRange(order.getPrice(), beginPrice, endPrice))
                .map(this::convertToDto)
                .sorted(Comparator.comparing(OrderDto::getPrice, Comparator.nullsFirst(BigDecimal::compareTo)))
                .collect(Collectors.toList());

        log.info("OpenOrdersObjImpl end exchange {}, symbol {} side {} beginPrice {} endPrice {} result {}",
                exchangeCode, symbol, side, beginPriceStr, endPriceStr, result);

        return result;
    }
}
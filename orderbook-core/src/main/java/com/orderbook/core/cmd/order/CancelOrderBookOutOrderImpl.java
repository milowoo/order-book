package com.orderbook.core.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.order.CancelOrderBookOutOrder;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.*;
import com.orderbook.core.store.OpenOrdersStore;
import com.orderbook.core.store.OrderBookStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Command(name = ExchangeFunc.CANCEL_ORDER_BOOK_OUT_ORDER)
@RequiredArgsConstructor
public class CancelOrderBookOutOrderImpl extends AbstractOrdersCmd implements CancelOrderBookOutOrder {

    @Autowired
    OpenOrdersStore openOrdersStore;
    @Autowired
    private OrderBookStore orderBookStore;

    @Override
    public Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("CancelOrderBookOutOrderImpl begin symbol {}", symbol);
        }
        long beginTime = System.currentTimeMillis();
        try {
            SymbolBo symbolBo = getSymbol(symbol);
            if (symbolBo == null) {
                log.warn("cancel order book out order symbol {} not found", symbol);
                return false;
            }

            OrderBook orderBook = orderBookStore.get(exchangeCode, symbolBo.getSymbolId());
            if (orderBook == null) return false;

            // 获取订单信息
            OpenOrdersBo openOrdersBo = openOrdersStore.getRemoteOpenOrders(symbolBo);
            Map<Long, OrderBo> orderBoMap = openOrdersBo.getOrders();

            // 取消订单簿外的订单
            cancelOutOrderBookOrder(symbolBo, orderBook, orderBoMap, "buy", apolloConfig.getOrderBookLimitLevel());
            cancelOutOrderBookOrder(symbolBo, orderBook, orderBoMap, "sell", apolloConfig.getOrderBookLimitLevel());
            return true;
        } catch (Exception e) {
            log.error("CancelOrderBetweenImpl call exception", e);
            return false;
        } finally {
            log.info("CancelOrderBookOutOrderImpl call cost: {} ms", System.currentTimeMillis() - beginTime);
        }
    }

    /**
     * 取消订单簿范围外的订单
     * @param symbolBo 交易对配置
     * @param orderBook 订单簿
     * @param orderBoMap 本地订单映射
     * @param side 买卖方向 "buy"/"sell"
     * @param limitLevel 价格档位限制
     */
    private void cancelOutOrderBookOrder(SymbolBo symbolBo, OrderBook orderBook, Map<Long, OrderBo> orderBoMap, String side, int limitLevel) {
        BigDecimal beginPrice;
        BigDecimal endPrice;

        if (side.equalsIgnoreCase("buy")) {
            beginPrice = BigDecimal.ZERO;
            List<PriceLevel> bid = orderBook.getBid();
            if (bid == null || bid.isEmpty()) {
                return;
            }
            if (bid.size() <= limitLevel) {
                return;
            }
            endPrice = bid.get(limitLevel).getPrice();
        } else {
            List<PriceLevel> ask = orderBook.getAsk();
            if (ask == null || ask.isEmpty()) {
                return;
            }
            if (ask.size() <= limitLevel) {
                return;
            }
            beginPrice = ask.get(limitLevel).getPrice();
            endPrice = new BigDecimal("2000000000.0");
        }

        // 根据 side 确定过滤器
        Predicate<OrderBo> sideFilter = "buy".equalsIgnoreCase(side)
                ? OrderBo::isBuyOrder
                : OrderBo::isSellOrder;

        List<OrderBo> filteredOrders = orderBoMap.values().stream()
                .filter(sideFilter)
                .filter(order -> !isWithinPriceRange(order.getPrice(), beginPrice, endPrice))
                .collect(Collectors.toList());

        if (filteredOrders.isEmpty()) {
            return;
        }
        batchCancelOrder(ExchangeCode.OSL_GLOBAL, symbolBo, filteredOrders, orderBoMap);
    }
}
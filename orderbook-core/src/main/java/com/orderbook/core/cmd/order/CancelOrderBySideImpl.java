package com.orderbook.core.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.order.CancelOrderBySide;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.OpenOrdersBo;
import com.orderbook.core.domain.OrderBo;
import com.orderbook.core.domain.SymbolBo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Command(name = ExchangeFunc.CANCEL_ORDER_BY_SIDE)
@RequiredArgsConstructor
class CancelOrderBySideImpl extends AbstractOrdersCmd implements CancelOrderBySide {

    @Override
    public Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side) {
        try {
            if (apolloConfig.isMMLogSwitch(symbol)) {
                log.info("cancel order by side begin exchange {} symbol {} side {}",
                        exchangeCode.name(), symbol, side);
            }

            SymbolBo symbolBo = getSymbol(symbol);
            if (symbolBo == null) {
                log.warn("cancel order by side no found symbol exchange {} symbol {} side {}",
                        exchangeCode.name(), symbol, side);
                return false;
            }

            OpenOrdersBo openOrdersBo = openOrdersStore.getOpenOrders(exchangeCode, symbolBo.getSymbolId());
            Map<Long, OrderBo> orderBoMap = openOrdersBo.getOrders();

            // 根据 side 确定过滤器
            Predicate<OrderBo> sideFilter = "Buy".equalsIgnoreCase(side)
                    ? OrderBo::isBuyOrder
                    : OrderBo::isSellOrder;

            List<OrderBo> filteredOrders = orderBoMap.values().stream()
                    .filter(sideFilter)
                    .collect(Collectors.toList());

            if (filteredOrders.isEmpty()) {
                if (apolloConfig.isMMLogSwitch(symbol)) {
                    log.info("cancel order no open orders exchange {} symbol {}",
                            exchangeCode.name(), symbol);
                }
                return true;
            }

            batchCancelOrder(ExchangeCode.OSL_GLOBAL, symbolBo, filteredOrders, orderBoMap);

            if (apolloConfig.isMMLogSwitch(symbol)) {
                log.info("CancelOrderBySideImpl end symbol {} side:{}", symbol, side);
            }
            return true;
        } catch (Exception e) {
            log.error("cancel order exception exchange {} symbol {}", exchangeCode.name(), symbol, e);
            return false;
        }
    }
}
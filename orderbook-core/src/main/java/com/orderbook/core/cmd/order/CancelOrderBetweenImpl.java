package com.orderbook.core.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.order.CancelOrderBetween;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.OpenOrdersBo;
import com.orderbook.core.domain.OrderBo;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OpenOrdersStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Command(name = ExchangeFunc.CANCEL_ORDER_BETWEEN)
@RequiredArgsConstructor
class CancelOrderBetweenImpl extends AbstractOrdersCmd implements CancelOrderBetween {

    @Autowired
    OpenOrdersStore openOrdersStore;

    @Override
    public Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side,
                        String beginPrice, String endPrice) {
        try {
            SymbolBo symbolBo = getSymbol(symbol);
            if (symbolBo == null) {
                log.warn("cancel order between symbol {} not found", symbol);
                return false;
            }

            // 获取订单信息
            OpenOrdersBo openOrdersBo = openOrdersStore.getRemoteOpenOrders(symbolBo);
            Map<Long, OrderBo> orderBoMap = openOrdersBo.getOrders();

            // 将字符串价格转为 BigDecimal
            BigDecimal beginPriceBig = parseBigDecimal(beginPrice, BigDecimal.ZERO);
            BigDecimal endPriceBig = parseBigDecimal(endPrice, BigDecimal.ZERO);

            // 根据 side 确定过滤器
            Predicate<OrderBo> sideFilter = "Buy".equalsIgnoreCase(side)
                    ? OrderBo::isBuyOrder
                    : OrderBo::isSellOrder;

            List<OrderBo> filteredOrders = orderBoMap.values().stream()
                    .filter(sideFilter)
                    .filter(order -> isWithinPriceRange(order.getPrice(), beginPriceBig, endPriceBig))
                    .collect(Collectors.toList());

            if (filteredOrders.isEmpty()) {
                if (apolloConfig.isMMLogSwitch(symbol)) {
                    log.info("cancel order between no order info symbol {} side {} beginPrice {} endPrice {}",
                            symbol, side, beginPrice, endPrice);
                }
                return true;
            }

            batchCancelOrder(getExchange(), symbolBo, filteredOrders, orderBoMap);
            return true;
        } catch (Exception e) {
            log.error("CancelOrderBetweenImpl call exception:", e);
            return false;
        }
    }
}
package com.orderbook.core.cmd.openorder;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.dto.OrderDto;
import com.orderbook.cmd.openorder.OpenOrdersAtPrice;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.OrderBo;
import com.orderbook.core.domain.SymbolBo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Command(name = ExchangeFunc.OPEN_ORDERS_AT)
@RequiredArgsConstructor
public class OpenOrdersAtPriceImpl extends AbstractOpenOrdersCmd implements OpenOrdersAtPrice {

    @Override
    public List<OrderDto> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String inPrice) {
        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("OpenOrdersAtPriceImpl call begin exchangeCode={} symbol={} side={} price={}",
                    exchangeCode.name(), symbol, side, inPrice);
        }

        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return Collections.emptyList();
        }

        BigDecimal price = new BigDecimal(inPrice);
        boolean isBuySide = "BUY".equalsIgnoreCase(side);

        List<OrderBo> allOrders = getAllBotOpenOrders(env, exchangeCode, symbolBo.getSymbolId());
        List<OrderDto> result = allOrders.stream()
                .filter(OrderBo::isLimitOrder)
                .filter(order -> isBuySide ? order.isBuyOrder() : order.isSellOrder())
                .filter(order -> order.getPrice().compareTo(price) == 0)
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return result;
    }
}
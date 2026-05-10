package com.orderbook.core.cmd;

import com.orderbook.cmd.dto.OrderDto;
import com.orderbook.core.config.ApolloConfig;
import com.orderbook.core.domain.OrderBo;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.SymbolStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

public abstract class BaseCmd {

    @Autowired
    protected SymbolStore symbolStore;

    @Autowired
    protected ApolloConfig apolloConfig;

    protected SymbolBo getSymbol(String symbol) {
        return symbolStore.findSymbolById(symbol);
    }

    protected BigDecimal parseBigDecimal(String value, BigDecimal defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue; // 或者抛异常，视业务需求而定
        }
    }

    protected boolean isWithinPriceRange(BigDecimal price, BigDecimal beginPrice, BigDecimal endPrice) {
        if (price == null) return false;
        return price.compareTo(beginPrice) >= 0 && price.compareTo(endPrice) <= 0;
    }

    protected OrderDto convertToDto(OrderBo bo) {
        return new OrderDto(String.valueOf(bo.getOrderId()), bo.getPrice(), bo.getAmount(), bo.getQuantity());
    }
}
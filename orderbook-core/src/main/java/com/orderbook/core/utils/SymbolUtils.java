package com.orderbook.core.utils;

import com.orderbook.core.domain.SymbolBo;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SymbolUtils {

    public static CurrencyPair fromSymbol(SymbolBo symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("Invalid symbol null");
        }
        return new CurrencyPair(symbol.getBaseTokenId(), symbol.getQuoteTokenId());
    }

    public static BigDecimal validateSize(BigDecimal size, BigDecimal stepSize) {
        if (stepSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Step size must be greater than zero.");
        }
        // 计算 size / stepSize，然后向下取整，再乘回来
        BigDecimal divided = size.divide(stepSize, 0, RoundingMode.DOWN);
        return divided.multiply(stepSize);
    }
}
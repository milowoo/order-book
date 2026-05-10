package com.orderbook.core.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.orderbook.BestPrice;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.cmd.BaseCmd;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OrderBookStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Command(name = ExchangeFunc.BEST_PRICE)
@RequiredArgsConstructor
public class BestPriceImpl extends BaseCmd implements BestPrice {

    private final OrderBookStore orderBookFactory;

    // 最优价格 best_price(exchange,symbol,side)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side) {
        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }

        OrderBook orderBook = orderBookFactory.get(exchangeCode, symbolBo.getSymbolId());
        if (orderBook == null) {
            return BigDecimal.ZERO;
        }

        if ("BUY".equalsIgnoreCase(side)) {
            return orderBook.getBestBidPrice();
        }
        return orderBook.getBestAskPrice();
    }

    // 最优价格 best_price(exchange,symbol,side,level)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String level) {
        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }

        OrderBook orderBook = orderBookFactory.get(exchangeCode, symbolBo.getSymbolId());
        if (orderBook == null) {
            return BigDecimal.ZERO;
        }

        if ("BUY".equalsIgnoreCase(side)) {
            return orderBook.getBidPrice(Integer.parseInt(level));
        }
        return orderBook.getAskPrice(Integer.parseInt(level));
    }
}
package com.orderbook.core.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.orderbook.MixBuyPrice;
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
@Command(name = ExchangeFunc.MIX_BUY_PRICE)
@RequiredArgsConstructor
public class MixBuyPriceImpl extends BaseCmd implements MixBuyPrice {

    private final OrderBookStore orderBookFactory;

    // 订单簿最大买价 mix_buy_price(exchange,symbol)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }

        OrderBook orderBook = orderBookFactory.get(exchangeCode, symbolBo.getSymbolId());
        if (orderBook == null) {
            return BigDecimal.ZERO;
        }

        if (orderBook.getBid() == null || orderBook.getBid().isEmpty()) {
            return BigDecimal.ZERO;
        }

        int index = orderBook.getBid().size();
        return orderBook.getBid().get(index - 1).getPrice();
    }
}
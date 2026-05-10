package com.orderbook.core.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.orderbook.MaxSellPrice;
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
@Command(name = ExchangeFunc.MAX_SELL_PRICE)
@RequiredArgsConstructor
public class MaxSellPriceImpl extends BaseCmd implements MaxSellPrice {

    private final OrderBookStore orderBookFactory;

    // 订单簿最大卖价 max_sell_price(exchange,symbol)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("max sell price call symbol:{}", symbol);
        }

        int maxSellPrice = apolloConfig.getMaxPlaceOrderLimit();
        log.info("max sell price call maxSellPrice:{}", maxSellPrice);

        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }

        OrderBook orderBook = orderBookFactory.get(exchangeCode, symbolBo.getSymbolId());
        if (orderBook == null) {
            return BigDecimal.ZERO;
        }

        if (orderBook.getAsk() == null || orderBook.getAsk().isEmpty()) {
            return BigDecimal.ZERO;
        }

        int index = orderBook.getAsk().size();
        BigDecimal price = orderBook.getAsk().get(index - 1).getPrice();

        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("max sell price :{}", price);
        }

        return price;
    }
}
package com.orderbook.core.cmd.price;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.price.LastPrice;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.cmd.BaseCmd;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.PriceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Command(name = ExchangeFunc.LAST_PRICE)
@RequiredArgsConstructor
public class LastPriceImpl extends BaseCmd implements LastPrice {

    @Autowired
    PriceStore priceStore;

    // 最新价格 last_price(exchange,symbol)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }
        return priceStore.getLastPrice(exchangeCode, symbolBo.getSymbolId());
    }
}
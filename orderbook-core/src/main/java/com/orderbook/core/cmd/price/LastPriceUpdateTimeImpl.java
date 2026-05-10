package com.orderbook.core.cmd.price;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.price.LastPriceUpdateTime;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.cmd.BaseCmd;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.PriceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

@Slf4j
@Command(name = ExchangeFunc.LAST_PRICE_UPDATE_TIME)
@RequiredArgsConstructor
public class LastPriceUpdateTimeImpl extends BaseCmd implements LastPriceUpdateTime {

    @Autowired
    PriceStore priceStore;

    // 最新价格更新时间 last_price_update_time(exchange,symbol)
    @Override
    public Long call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return 0L;
        }
        return priceStore.getLastTime(exchangeCode, symbolBo.getSymbolId());
    }
}
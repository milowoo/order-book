package com.orderbook.core.cmd.instrument;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.instrument.MinSize;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.cmd.BaseCmd;
import com.orderbook.core.domain.SymbolBo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Command(name = ExchangeFunc.MIN_SIZE)
@RequiredArgsConstructor
public class MinSizeImpl extends BaseCmd implements MinSize {

    // 最小下单数量 min_size(exchange,symbol)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }
        return symbolBo.getMinSize();
    }
}
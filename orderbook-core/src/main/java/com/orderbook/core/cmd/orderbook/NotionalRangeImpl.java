package com.orderbook.core.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.orderbook.NotionalRange;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.cmd.BaseCmd;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.PriceLevel;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OrderBookStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Command(name = ExchangeFunc.NOTIONAL_RANGE)
@RequiredArgsConstructor
public class NotionalRangeImpl extends BaseCmd implements NotionalRange {

    private final OrderBookStore orderBookFactory;

    // 价格区间内订单notional notional_range(exchange,symbol,side,beginPrice,endPrice)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol,
                           String side, String beginPrice, String endPrice) {
        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }

        OrderBook orderBook = orderBookFactory.get(exchangeCode, symbolBo.getSymbolId());
        if (orderBook == null) {
            return BigDecimal.ZERO;
        }

        // 将字符串价格转为 BigDecimal
        BigDecimal beginPriceBig = parseBigDecimal(beginPrice, BigDecimal.ZERO);
        BigDecimal endPriceBig = parseBigDecimal(endPrice, BigDecimal.ZERO);

        List<PriceLevel> priceLevels;
        if ("SELL".equalsIgnoreCase(side)) {
            priceLevels = orderBook.getAsk(); // 卖单队列（买方挂单）
        } else {
            priceLevels = orderBook.getBid(); // 买单队列（卖方挂单）
        }

        // 使用 Stream 过滤指定价格区间内的订单
        return priceLevels.stream()
                .filter(level -> {
                    BigDecimal price = level.getPrice();
                    BigDecimal min = beginPriceBig.min(endPriceBig);
                    BigDecimal max = beginPriceBig.max(endPriceBig);
                    return price.compareTo(min) >= 0 && price.compareTo(max) <= 0;
                })
                .map(PriceLevel::notional)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
package com.orderbook.core.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.orderbook.BaseVolumeRange;
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
@Command(name = ExchangeFunc.BASE_VOLUME_RANGE)
@RequiredArgsConstructor
public class BaseVolumeRangeImpl extends BaseCmd implements BaseVolumeRange {

    private final OrderBookStore orderBookStore;

    // 价格区间内订单base volume notional_range(exchange,symbol,side,beginPrice,endPrice)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol,
                           String side, String beginPrice, String endPrice) {
        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }

        OrderBook orderBook = orderBookStore.get(exchangeCode, symbolBo.getSymbolId());
        if (orderBook == null) {
            return BigDecimal.ZERO;
        }

        // 将字符串价格转为 BigDecimal
        BigDecimal beginPriceBig = parseBigDecimal(beginPrice, BigDecimal.ZERO);
        BigDecimal endPriceBig = parseBigDecimal(endPrice, BigDecimal.ZERO);

        List<PriceLevel> priceLevels = "BUY".equalsIgnoreCase(side)
                ? orderBook.getAsk()
                : orderBook.getBid();

        return priceLevels.stream()
                .filter(level -> {
                    BigDecimal price = level.getPrice();
                    return price.compareTo(beginPriceBig) >= 0 && price.compareTo(endPriceBig) <= 0;
                })
                .map(PriceLevel::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
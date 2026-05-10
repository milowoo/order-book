package com.orderbook.core.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.cmd.BaseCmd;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OrderBookStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Command(name = ExchangeFunc.PRICE_LEVELS)
@RequiredArgsConstructor
public class PriceLevelImpl extends BaseCmd implements com.orderbook.cmd.orderbook.PriceLevel {

    private final OrderBookStore orderBookFactory;

    /**
     * 价格level price_level(exchange,symbol,side)
     */
    @Override
    public List<BigDecimal> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side) {
        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return new ArrayList<>();
        }

        OrderBook orderBook = orderBookFactory.get(exchangeCode, symbolBo.getSymbolId());
        if (orderBook == null) {
            return new ArrayList<>();
        }

        //获取对应方向的价格队列
        List<com.orderbook.core.domain.PriceLevel> queue = "SELL".equalsIgnoreCase(side)
                ? orderBook.getAsk()
                : orderBook.getBid();

        // 使用 Stream 过滤出在 [low, high] 范围内的价格
        return queue.stream()
                .map(com.orderbook.core.domain.PriceLevel::getPrice)
                .sorted() // 可选: 保持结果有序
                .collect(Collectors.toList());
    }

    /**
     * 价格level price_level(exchange,symbol,side,beginPrice,endPrice)
     */
    @Override
    public List<BigDecimal> call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol,
                                 String side, String beginPrice, String endPrice) {
        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return new ArrayList<>();
        }

        OrderBook orderBook = orderBookFactory.get(exchangeCode, symbolBo.getSymbolId());
        if (orderBook == null) {
            return new ArrayList<>();
        }

        // 将字符串价格转为 BigDecimal
        BigDecimal beginPriceBig = parseBigDecimal(beginPrice, BigDecimal.ZERO);
        BigDecimal endPriceBig = parseBigDecimal(endPrice, BigDecimal.ZERO);

        // 确保 low <= high
        BigDecimal low = beginPriceBig.min(endPriceBig);
        BigDecimal high = beginPriceBig.max(endPriceBig);

        //获取对应方向的价格队列
        List<com.orderbook.core.domain.PriceLevel> queue = "SELL".equalsIgnoreCase(side)
                ? orderBook.getAsk()
                : orderBook.getBid();

        // 使用 Stream 过滤出在 [low, high] 范围内的价格
        return queue.stream()
                .map(com.orderbook.core.domain.PriceLevel::getPrice)
                .filter(price -> price.compareTo(low) >= 0 && price.compareTo(high) <= 0)
                .sorted() // 可选: 保持结果有序
                .collect(Collectors.toList());
    }
}
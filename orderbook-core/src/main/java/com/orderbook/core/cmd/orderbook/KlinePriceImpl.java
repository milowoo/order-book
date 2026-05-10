package com.orderbook.core.cmd.orderbook;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.orderbook.KlinePrice;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.cmd.BaseCmd;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.PriceLevel;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.utils.MathUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Command(name = ExchangeFunc.KLINE_PRICE)
@RequiredArgsConstructor
public class KlinePriceImpl extends BaseCmd implements KlinePrice {

    private final OrderBookStore orderBookFactory;

    // 价格level price_level(exchange,symbol,side,level)
    @Override
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String side, String level) {
        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("kline price call symbol:{} side:{} level:{}", symbol, side, level);
        }

        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }

        OrderBook orderBook = orderBookFactory.get(exchangeCode, symbolBo.getSymbolId());
        if (orderBook == null) {
            return BigDecimal.ZERO;
        }

        int len = Integer.parseInt(level);
        // 获取对应方向的价格队列
        List<PriceLevel> queue = "SELL".equalsIgnoreCase(side)
                ? orderBook.getAsk()
                : orderBook.getBid();

        int limit = Math.min(len, queue.size());
        if (limit <= 0) {
            return BigDecimal.ZERO;
        }
        int rand = MathUtils.random(limit);
        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("kline price end symbol:{} side:{} level:{} price:{}", symbol, side, level, queue.get(rand).getPrice());
        }
        return queue.get(rand).getPrice();
    }

    // 价格level price_level(exchange,symbol,level) - backwards compatible
    public BigDecimal call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String level) {
        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("kline price call symbol:{} level:{}", symbol, level);
        }

        SymbolBo symbolBo = getSymbol(symbol);
        if (symbolBo == null) {
            return BigDecimal.ZERO;
        }

        OrderBook orderBook = orderBookFactory.get(exchangeCode, symbolBo.getSymbolId());
        if (orderBook == null) {
            return BigDecimal.ZERO;
        }

        int len = Integer.parseInt(level);
        String side = MathUtils.random(2) == 1 ? "SELL" : "BUY";
        // 获取对应方向的价格队列
        List<PriceLevel> queue = "SELL".equalsIgnoreCase(side)
                ? orderBook.getAsk()
                : orderBook.getBid();

        int limit = Math.min(len, queue.size());
        int rand = MathUtils.random(limit);
        if (apolloConfig.isMMLogSwitch(symbol)) {
            log.info("kline price end symbol:{} level:{} price:{}", symbol, level, queue.get(rand).getPrice());
        }
        return queue.get(rand).getPrice();
    }
}
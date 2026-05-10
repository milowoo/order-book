package com.orderbook.core.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.order.KlineMaker;
import com.orderbook.connector.common.dto.*;
import com.orderbook.connector.global.GlobalExchange;
import com.orderbook.connector.global.service.GlobalTradeService;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.OrderBook;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.store.OrderBookStore;
import com.orderbook.core.utils.IdGenerator;
import com.orderbook.core.utils.MathUtils;
import com.orderbook.core.utils.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Command(name = ExchangeFunc.KLINE_MAKER)
@RequiredArgsConstructor
public class KlineMakerImpl extends AbstractOrdersCmd implements KlineMaker {

    @Autowired
    private OrderBookStore orderBookStore;

    // 针对某个symbol进行kline画线
    // Params:
    //   env
    //   exchangeCode
    //   symbol
    // Returns:
    @Override
    public Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        long beginTime = System.currentTimeMillis();
        try {
            SymbolBo symbolBo = getSymbol(symbol);
            if (symbolBo == null) {
                log.warn("invalid exchange symbol {}", symbol);
                return false;
            }

            OrderBook bybitOrderBook = orderBookStore.get(ExchangeCode.BYBIT, symbolBo.getSymbolId());
            String sideFirst = "buy";
            BigDecimal price = bybitOrderBook.bidsQueue().get(0).getPrice();
            BigDecimal qty = symbolBo.getMinSize();
            BigDecimal minAmount = new BigDecimal("5"); // 最小下单额度(USD)
            BigDecimal amount = price.multiply(qty);

            // 若下单金额 ≤ 5U，则按 10U 额度反推下单数量
            if (amount.compareTo(minAmount) <= 0) {
                qty = new BigDecimal("10").divide(price, 18, RoundingMode.DOWN);
            }
            // 格式化数量，确保符合币对的最小精度要求
            qty = SymbolUtils.validateSize(qty, symbolBo.getMinSize());

            int random = MathUtils.random(100);
            if (random <= 50) {
                sideFirst = "sell";
                price = bybitOrderBook.asksQueue().get(0).getPrice();
            }

            GlobalExchange exchange = (GlobalExchange) connectorFactory.getExchange(exchangeCode, true);
            GlobalTradeService tradeService = (GlobalTradeService) exchange.getTradeService();
            SpotBatchOrdersDto request = convertBatchOrders(
                    symbolBo.getSymbol(),
                    price.toPlainString(),
                    qty.toPlainString(),
                    sideFirst
            );
            batchKlineOrder(tradeService, request, symbolBo);
            return true;
        } catch (Exception e) {
            log.error("KlineOrderImpl call exception", e);
            return false;
        } finally {
            log.info("KlineOrderImpl call cost: {} ms", System.currentTimeMillis() - beginTime);
        }
    }

    private SpotBatchOrdersDto convertBatchOrders(String symbol, String inPrice, String qty, String firstSide) {
        SpotBatchOrdersDto request = new SpotBatchOrdersDto();
        request.setSymbol(symbol);
        List<SpotOrdersV2Req> orderList = new ArrayList<>();

        // 限价卖
        SpotOrdersV2Req sellRequest = new SpotOrdersV2Req();
        sellRequest.setClientOid(IdGenerator.snakeFlowId());
        sellRequest.setOrderType("limit");
        sellRequest.setSide("sell");
        sellRequest.setPrice(inPrice);
        sellRequest.setSize(qty);

        // 限价买
        SpotOrdersV2Req buyRequest = new SpotOrdersV2Req();
        buyRequest.setClientOid(IdGenerator.snakeFlowId());
        buyRequest.setSide("buy");
        buyRequest.setOrderType("limit");
        buyRequest.setPrice(inPrice);
        buyRequest.setSize(qty);

        if (firstSide.equalsIgnoreCase("sell")) {
            sellRequest.setForce("GTC");
            buyRequest.setForce("POST_ONLY");
            orderList.add(sellRequest);
            orderList.add(buyRequest);
        } else {
            buyRequest.setForce("GTC");
            sellRequest.setForce("POST_ONLY");
            orderList.add(buyRequest);
            orderList.add(sellRequest);
        }

        request.setOrderList(orderList);
        return request;
    }
}
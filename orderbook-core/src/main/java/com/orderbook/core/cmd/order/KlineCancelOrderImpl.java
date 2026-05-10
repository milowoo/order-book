package com.orderbook.core.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.order.KlineCancelOrder;
import com.orderbook.connector.global.GlobalExchange;
import com.orderbook.connector.global.service.GlobalTradeService;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.utils.OrderUtils;
import com.orderbook.core.utils.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Command(name = ExchangeFunc.KLINE_CANCEL_ORDER)
@RequiredArgsConstructor
public class KlineCancelOrderImpl extends AbstractOrdersCmd implements KlineCancelOrder {

    // cancel_order(orderId)
    @Override
    public Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String orderId) {
        try {
            if (apolloConfig.isMMLogSwitch(symbol)) {
                log.info("cancel order begin exchange {} symbol {} orderId {}", exchangeCode.name(), symbol, orderId);
            }

            SymbolBo symbolBo = getSymbol(symbol);
            if (symbolBo == null) {
                return false;
            }

            CurrencyPair currencyPair = SymbolUtils.fromSymbol(symbolBo);
            GlobalExchange exchange = (GlobalExchange) connectorFactory.getExchange(exchangeCode, true);
            GlobalTradeService tradeService = (GlobalTradeService) exchange.getTradeService();
            CancelOrderParams params = OrderUtils.createCancelOrderParams(exchangeCode, currencyPair, orderId);
            klineCancelOrder(tradeService, params, symbolBo);
            return true;
        } catch (Exception e) {
            log.error("cancel order exception exchange {} orderId {}", exchangeCode.name(), orderId, e);
        }
        return false;
    }

    // 取消某个symbol的订单 cancel_order(symbol)
    // Params:
    //   env
    //   exchangeCode
    //   symbol
    // Returns:
    @Override
    public Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        try {
            if (apolloConfig.isMMLogSwitch(symbol)) {
                log.info("cancel order begin exchange {} symbol {}", exchangeCode.name(), symbol);
            }

            SymbolBo symbolBo = getSymbol(symbol);
            if (symbolBo == null) {
                return false;
            }

            Exchange exchange = connectorFactory.getExchange(exchangeCode, true);
            GlobalTradeService tradeService = (GlobalTradeService) exchange.getTradeService();
            OpenOrdersParams params = OrderUtils.createOpenOrdersParams(exchangeCode, symbolBo);
            List<BitgetOrderInfoDto> orders = tradeService.getOpenOrdersNew(params);

            if (orders == null || orders.isEmpty()) {
                if (apolloConfig.isMMLogSwitch(symbol)) {
                    log.info("cancel order no open orders exchange {} symbol {}", exchangeCode.name(), symbol);
                }
                return true;
            }

            List<String> orderList = filterOldOrderIds(orders);
            batchCancelOrder(tradeService, symbolBo, orderList);
            return true;
        } catch (Exception e) {
            log.error("cancel order exception exchange {} symbol {}", exchangeCode.name(), symbol, e);
        }
        return false;
    }

    public List<String> filterOldOrderIds(List<BitgetOrderInfoDto> orders) {
        Instant now = Instant.now();
        // 过滤出 updatedAt 距离当前时间超过 2 秒的订单
        return orders.stream()
                .filter(order -> order.getUpdatedAt() != null &&
                        order.getUpdatedAt().isBefore(now.minusSeconds(2)))
                .map(BitgetOrderInfoDto::getOrderId)
                .collect(Collectors.toList());
    }
}
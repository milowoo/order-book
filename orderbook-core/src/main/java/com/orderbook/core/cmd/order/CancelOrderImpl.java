package com.orderbook.core.cmd.order;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.cmd.ExchangeFunc;
import com.orderbook.cmd.order.CancelOrder;
import com.orderbook.core.annotation.Command;
import com.orderbook.core.domain.OpenOrdersBo;
import com.orderbook.core.domain.SymbolBo;
import com.orderbook.core.utils.OrderUtils;
import com.orderbook.core.utils.SymbolUtils;
import com.orderbook.connector.global.GlobalExchange;
import com.orderbook.connector.global.service.GlobalTradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.trade.params.CancelOrderParams;

import java.util.Map;

@Slf4j
@Command(name = ExchangeFunc.CANCEL_ORDER)
@RequiredArgsConstructor
class CancelOrderImpl extends AbstractOrdersCmd implements CancelOrder {

    @Override
    public Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol) {
        return true;
    }

    @Override
    public Boolean call(Map<String, Object> env, ExchangeCode exchangeCode, String symbol, String orderId) {
        try {
            if (apolloConfig.isMMLogSwitch(symbol)) {
                log.info("cancel order begin exchange {} symbol {} orderId {}",
                        exchangeCode.name(), symbol, orderId);
            }

            SymbolBo symbolBo = getSymbol(symbol);
            if (symbolBo == null) {
                log.warn("cancel order no symbol info exchange {} symbol {} orderId {}",
                        exchangeCode.name(), symbol, orderId);
                return false;
            }

            OpenOrdersBo openOrdersBo = openOrdersStore.getOpenOrders(exchangeCode, symbolBo.getSymbolId());
            GlobalExchange exchange = (GlobalExchange) connectorFactory.getTradingExchange(
                    exchangeCode,
                    symbolBo.getApiKey(),
                    symbolBo.getSecretKey(),
                    symbolBo.getPassword()
            );
            GlobalTradeService tradeService = (GlobalTradeService) exchange.getTradeService();
            CurrencyPair currencyPair = SymbolUtils.fromSymbol(symbolBo);
            CancelOrderParams params = OrderUtils.createCancelOrderParams(exchangeCode, currencyPair, orderId);
            boolean result = tradeService.cancelOrder(params);

            if (result) {
                openOrdersBo.removeOrderById(Long.parseLong(orderId));
            }

            if (apolloConfig.isMMLogSwitch(symbol)) {
                log.info("cancel order end exchange {} symbol {} orderId {} result {}",
                        exchangeCode.name(), symbol, orderId, result);
            }

            return true;
        } catch (Exception e) {
            log.error("cancel order exception exchange {} symbol {}", exchangeCode.name(), symbol, e);
            return false;
        }
    }
}
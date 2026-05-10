package com.orderbook.connector.common.dto;

import lombok.AllArgsConstructor;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.trade.params.CancelOrderByCurrencyPair;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderByUserReferenceParams;

@AllArgsConstructor
public class BitgetCancelOrderParam implements CancelOrderByUserReferenceParams, CancelOrderByIdParams, CancelOrderByCurrencyPair {

    private String orderId;
    private CurrencyPair currencyPair;
    private String userReference;

    @Override
    public String getUserReference() {
        return userReference;
    }

    @Override
    public String getOrderId() {
        return orderId;
    }

    @Override
    public CurrencyPair getCurrencyPair() {
        return currencyPair;
    }

    public BitgetCancelOrderParam(CurrencyPair pair, String orderId) {
        this.currencyPair = pair;
        this.orderId = orderId;
    }
}
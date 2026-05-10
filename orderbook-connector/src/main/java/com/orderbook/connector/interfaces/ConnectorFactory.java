package com.orderbook.connector.interfaces;

import com.orderbook.cmd.ExchangeCode;
import org.knowm.xchange.Exchange;

public interface ConnectorFactory {

    Exchange getExchange(ExchangeCode exchange, boolean needAuth);

    Exchange getTradingExchange(ExchangeCode exchange, String apiKey, String secretKey, String pwd);
}
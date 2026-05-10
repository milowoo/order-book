package com.orderbook.connector.global.service;

import com.orderbook.connector.global.GlobalAuthenticated;
import com.orderbook.connector.global.Global;
import com.orderbook.connector.global.GlobalExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitget.config.BitgetJacksonObjectMapperFactory;
import org.knowm.xchange.bitget.service.BitgetDigest;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

public class GlobalBaseService extends BaseExchangeService<GlobalExchange> implements BaseService {

    protected final String apiKey;
    protected final String passphrase;
    protected final Global global;
    protected final GlobalAuthenticated bitgetAuthenticated;
    protected final BitgetDigest bitgetDigest;
    protected final Integer paptrading;

    public GlobalBaseService(GlobalExchange exchange) {
        super(exchange);

        global =
                ExchangeRestProxyBuilder.forInterface(Global.class, exchange.getExchangeSpecification())
                        .clientConfigCustomizer(
                                clientConfig ->
                                        clientConfig.setJacksonObjectMapperFactory(
                                                new BitgetJacksonObjectMapperFactory()))
                        .build();

        bitgetAuthenticated =
                ExchangeRestProxyBuilder.forInterface(
                        GlobalAuthenticated.class, exchange.getExchangeSpecification())
                        .clientConfigCustomizer(
                                clientConfig ->
                                        clientConfig.setJacksonObjectMapperFactory(
                                                new BitgetJacksonObjectMapperFactory()))
                        .build();

        apiKey = exchange.getExchangeSpecification().getApiKey();
        passphrase = exchange.getExchangeSpecification().getPassword();
        bitgetDigest = BitgetDigest.createInstance(exchange.getExchangeSpecification().getSecretKey());
        paptrading = (Boolean) exchange.getExchangeSpecification()
                .getExchangeSpecificParametersItem(Exchange.USE_SANDBOX) ? 1 : 0;
    }
}
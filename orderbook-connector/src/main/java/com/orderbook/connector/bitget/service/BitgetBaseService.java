package com.orderbook.connector.bitget.service;

import com.orderbook.connector.bitget.Bitget;
import com.orderbook.connector.bitget.BitgetAuthenticated;
import com.orderbook.connector.bitget.BitgetExchange;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitget.config.BitgetJacksonObjectMapperFactory;
import org.knowm.xchange.bitget.service.BitgetDigest;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.service.BaseExchangeService;
import org.knowm.xchange.service.BaseService;

public class BitgetBaseService extends BaseExchangeService<BitgetExchange> implements BaseService {

    protected final String apiKey;
    protected final String passphrase;
    protected final Bitget bitget;
    protected final BitgetAuthenticated bitgetAuthenticated;
    protected final BitgetDigest bitgetDigest;
    protected final Integer paptrading;

    public BitgetBaseService(BitgetExchange exchange) {
        super(exchange);

        bitget =
                ExchangeRestProxyBuilder.forInterface(Bitget.class, exchange.getExchangeSpecification())
                        .clientConfigCustomizer(
                                clientConfig ->
                                        clientConfig.setJacksonObjectMapperFactory(
                                                new BitgetJacksonObjectMapperFactory()))
                        .build();

        bitgetAuthenticated =
                ExchangeRestProxyBuilder.forInterface(
                        BitgetAuthenticated.class, exchange.getExchangeSpecification())
                        .clientConfigCustomizer(
                                clientConfig ->
                                        clientConfig.setJacksonObjectMapperFactory(
                                                new BitgetJacksonObjectMapperFactory()))
                        .build();

        apiKey = exchange.getExchangeSpecification().getApiKey();
        passphrase = exchange.getExchangeSpecification().getPassword();
        bitgetDigest = BitgetDigest.createInstance(exchange.getExchangeSpecification().getSecretKey());
        paptrading = (Boolean) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("USE_SANDBOX") ? 1 : 0;
    }
}
package com.orderbook.connector.interfaces.impl;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.interfaces.StreamConnectorFactory;
import com.orderbook.connector.stream.binance.BinanceStreamingExchange;
import com.orderbook.connector.stream.bitget.BitgetStreamingExchange;
import com.orderbook.connector.stream.bybit.BybitStreamingExchange;
import com.orderbook.connector.stream.global.GlobalStreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.knowm.xchange.bybit.BybitExchange.SPECIFIC_PARAM_TESTNET;

@Component("streamConnectorFactory")
@RequiredArgsConstructor
public class StreamConnectorFactoryImpl implements StreamConnectorFactory {

    private final Map<ExchangeCode, StreamingExchange> PUBLIC_EXCHANGE_MAP = new ConcurrentHashMap<>();
    private final Map<ExchangeCode, StreamingExchange> PRIVATE_EXCHANGE_MAP = new ConcurrentHashMap<>();
    private final Map<String, StreamingExchange> TRADING_MAP = new ConcurrentHashMap<>();
    private final Environment environment;

    @Override
    public Exchange getExchange(ExchangeCode exchange, boolean needAuth) {
        if (needAuth && PRIVATE_EXCHANGE_MAP.containsKey(exchange)) {
            return PRIVATE_EXCHANGE_MAP.get(exchange);
        } else if (!needAuth && PUBLIC_EXCHANGE_MAP.containsKey(exchange)) {
            return PUBLIC_EXCHANGE_MAP.get(exchange);
        }

        ExchangeSpecification exSpec = null;
        Class<? extends StreamingExchange> exchangeClass = null;
        String isUseDemo = null;

        switch (exchange) {
            case BINANCE:
                if (needAuth) {
                    exSpec = new BinanceStreamingExchange().getDefaultExchangeSpecification();
                    String apiKey = environment.getProperty(String.format("connect.exchange.%s.api_key", exchange));
                    if (StringUtils.isEmpty(apiKey)) {
                        throw new UnsupportedOperationException("need api_key config!");
                    }
                    String secretKey = environment.getProperty(String.format("connect.exchange.%s.secret_key", exchange));
                    if (StringUtils.isEmpty(secretKey)) {
                        throw new UnsupportedOperationException("need secret_key config!");
                    }
                    boolean isUserSandBox = false;
                    String useSandBox = environment.getProperty(String.format("connect.exchange.%s.use_sandbox", exchange));
                    if (StringUtils.isNotBlank(useSandBox) && "true".equalsIgnoreCase(useSandBox)) {
                        isUserSandBox = true;
                    }
                    exSpec.setApiKey(apiKey);
                    exSpec.setSecretKey(secretKey);
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, isUserSandBox);
                } else {
                    exchangeClass = BinanceStreamingExchange.class;
                }
                break;
            case BITGET:
                exSpec = new BitgetStreamingExchange().getDefaultExchangeSpecification();
                isUseDemo = environment.getProperty(String.format("connect.exchange.%s.is_use_demo", exchange));
                if (StringUtils.isNotBlank(isUseDemo) && "true".equals(isUseDemo)) {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);
                } else {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, false);
                }
                if (needAuth) {
                    String apiKey = environment.getProperty(String.format("connect.exchange.%s.api_key", exchange));
                    if (StringUtils.isEmpty(apiKey)) {
                        throw new UnsupportedOperationException("need api_key config!");
                    }
                    String secretKey = environment.getProperty(String.format("connect.exchange.%s.secret_key", exchange));
                    if (StringUtils.isEmpty(secretKey)) {
                        throw new UnsupportedOperationException("need secret_key config!");
                    }
                    String password = environment.getProperty(String.format("connect.exchange.%s.password", exchange));
                    if (StringUtils.isEmpty(password)) {
                        throw new UnsupportedOperationException("need password config!");
                    }
                    exSpec.setApiKey(apiKey);
                    exSpec.setSecretKey(secretKey);
                    exSpec.setPassword(password);
                }
                break;
            case BYBIT:
                exSpec = new BybitStreamingExchange().getDefaultExchangeSpecification();
                isUseDemo = environment.getProperty(String.format("connect.exchange.%s.is_use_demo", exchange));
                String isUseTestNet = environment.getProperty(String.format("connect.exchange.%s.is_use_testnet", exchange));
                if (StringUtils.isNotBlank(isUseDemo) && StringUtils.isNotBlank(isUseTestNet) && isUseDemo.equals("true") && isUseTestNet.equals("true")) {
                    throw new UnsupportedOperationException("is_user_demo and is_use_testnet can't all ture!");
                }
                if (StringUtils.isNotBlank(isUseDemo) && "true".equals(isUseDemo)) {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);
                }
                if (StringUtils.isNotBlank(isUseTestNet) && "true".equals(isUseTestNet)) {
                    exSpec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, true);
                }
                if (needAuth) {
                    String apiKey = environment.getProperty(String.format("connect.exchange.%s.api_key", exchange));
                    if (StringUtils.isEmpty(apiKey)) {
                        throw new UnsupportedOperationException("need api_key config!");
                    }
                    String secretKey = environment.getProperty(String.format("connect.exchange.%s.secret_key", exchange));
                    if (StringUtils.isEmpty(secretKey)) {
                        throw new UnsupportedOperationException("need secret_key config!");
                    }
                    exSpec.setApiKey(apiKey);
                    exSpec.setSecretKey(secretKey);
                }
                break;
            case OSL_GLOBAL:
                exSpec = new GlobalStreamingExchange().getDefaultExchangeSpecification();
                isUseDemo = environment.getProperty(String.format("connect.exchange.%s.is_use_demo", exchange));
                if (StringUtils.isNotBlank(isUseDemo) && "true".equals(isUseDemo)) {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);
                } else {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, false);
                }
                if (needAuth) {
                    String apiKey = environment.getProperty(String.format("connect.exchange.%s.api_key", exchange));
                    if (StringUtils.isEmpty(apiKey)) {
                        throw new UnsupportedOperationException("need api_key config!");
                    }
                    String secretKey = environment.getProperty(String.format("connect.exchange.%s.secret_key", exchange));
                    if (StringUtils.isEmpty(secretKey)) {
                        throw new UnsupportedOperationException("need secret_key config!");
                    }
                    String password = environment.getProperty(String.format("connect.exchange.%s.password", exchange));
                    if (StringUtils.isEmpty(password)) {
                        throw new UnsupportedOperationException("need password config!");
                    }
                    exSpec.setApiKey(apiKey);
                    exSpec.setSecretKey(secretKey);
                    exSpec.setPassword(password);
                }
                break;
            default:
                break;
        }

        if (exSpec != null) {
            if (needAuth) {
                synchronized (PRIVATE_EXCHANGE_MAP) {
                    if (PRIVATE_EXCHANGE_MAP.containsKey(exchange)) {
                        return PRIVATE_EXCHANGE_MAP.get(exchange);
                    }
                    StreamingExchange exchangeInstance = StreamingExchangeFactory.INSTANCE.createExchange(exSpec);
                    PRIVATE_EXCHANGE_MAP.put(exchange, exchangeInstance);
                    return exchangeInstance;
                }
            } else {
                synchronized (PUBLIC_EXCHANGE_MAP) {
                    if (PUBLIC_EXCHANGE_MAP.containsKey(exchange)) {
                        return PUBLIC_EXCHANGE_MAP.get(exchange);
                    }
                    StreamingExchange exchangeInstance = StreamingExchangeFactory.INSTANCE.createExchange(exSpec);
                    PUBLIC_EXCHANGE_MAP.put(exchange, exchangeInstance);
                    return exchangeInstance;
                }
            }
        } else if (exchangeClass != null) {
            synchronized (PUBLIC_EXCHANGE_MAP) {
                if (PUBLIC_EXCHANGE_MAP.containsKey(exchange)) {
                    return PUBLIC_EXCHANGE_MAP.get(exchange);
                }
                StreamingExchange exchangeInstance = StreamingExchangeFactory.INSTANCE.createExchange(exchangeClass);
                PUBLIC_EXCHANGE_MAP.put(exchange, exchangeInstance);
                return exchangeInstance;
            }
        }
        throw new UnsupportedOperationException("Unsupported exchange: " + exchange);
    }

    @Override
    public Exchange getTradingExchange(ExchangeCode exchange, String apiKey, String secretKey, String pwd) {
        if (TRADING_MAP.get(apiKey) != null) {
            return TRADING_MAP.get(apiKey);
        }

        ExchangeSpecification exSpec = null;
        String isUseDemo = null;
        switch (exchange) {
            case OSL_GLOBAL:
                exSpec = new GlobalStreamingExchange().getDefaultExchangeSpecification();
                isUseDemo = environment.getProperty(String.format("connect.exchange.%s.is_use_demo", exchange));
                if (StringUtils.isNotBlank(isUseDemo) && "true".equals(isUseDemo)) {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);
                } else {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, false);
                }
                String apiUrl = environment.getProperty(String.format("connect.exchange.%s.api_url", exchange));
                if (StringUtils.isEmpty(apiUrl)) {
                    throw new UnsupportedOperationException("need apiUrl config!");
                }
                String host = environment.getProperty(String.format("connect.exchange.%s.host", exchange));
                if (StringUtils.isEmpty(host)) {
                    throw new UnsupportedOperationException("need host config!");
                }
                exSpec.setSslUri(apiUrl);
                exSpec.setHost(host);
                exSpec.setApiKey(apiKey);
                exSpec.setSecretKey(secretKey);
                exSpec.setPassword(pwd);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported exchange: " + exchange);
        }

        synchronized (TRADING_MAP) {
            if (TRADING_MAP.containsKey(apiKey)) {
                return TRADING_MAP.get(apiKey);
            }
            StreamingExchange exchangeInstance = StreamingExchangeFactory.INSTANCE.createExchange(exSpec);
            TRADING_MAP.put(apiKey, exchangeInstance);
            return exchangeInstance;
        }
    }
}
package com.orderbook.connector.interfaces.impl;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.connector.bitget.BitgetExchange;
import com.orderbook.connector.global.GlobalExchange;
import com.orderbook.connector.interfaces.ConnectorFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.bybit.BybitExchange;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.knowm.xchange.bybit.BybitExchange.SPECIFIC_PARAM_TESTNET;

@Component
@RequiredArgsConstructor
public class ConnectorFactoryImpl implements ConnectorFactory {

    private final Map<ExchangeCode, Exchange> PUBLIC_EXCHANGE_MAP = new ConcurrentHashMap<>();
    private final Map<ExchangeCode, Exchange> PRIVATE_EXCHANGE_MAP = new ConcurrentHashMap<>();
    private final Map<String, Exchange> TRADING_MAP = new ConcurrentHashMap<>();
    private final Environment environment;

    @Override
    public Exchange getTradingExchange(ExchangeCode exchange, String apiKey, String secretKey, String pwd) {
        if (TRADING_MAP.get(apiKey) != null) {
            return TRADING_MAP.get(apiKey);
        }

        ExchangeSpecification exSpec = null;
        String isUseDemo = null;
        switch (exchange) {
            case OSL_GLOBAL:
                exSpec = new GlobalExchange().getDefaultExchangeSpecification();
                isUseDemo = environment.getProperty(String.format("connect.exchange.%s.is_use_demo", exchange));
                if (StringUtils.isNotBlank(isUseDemo) && "true".equals(isUseDemo)) {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);
                } else {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, false);
                }
                break;
            case BINANCE:
                exSpec = new BinanceExchange().getDefaultExchangeSpecification();
                String apiUrl = environment.getProperty(String.format("connect.exchange.%s.api_url", exchange));
                if (StringUtils.isBlank(apiUrl)) {
                    throw new RuntimeException("need api_url config!");
                }
                exSpec.setSslUri(apiUrl);
                break;
            case BYBIT:
                exSpec = new BybitExchange().getDefaultExchangeSpecification();
                isUseDemo = environment.getProperty(String.format("connect.exchange.%s.is_use_demo", exchange));
                String isUseTestNet = environment.getProperty(String.format("connect.exchange.%s.is_use_testnet", exchange));
                // 走卖盘还是模拟盘、测试网判断
                if (StringUtils.isNotBlank(isUseDemo) && StringUtils.isNotBlank(isUseTestNet) && isUseDemo.equals("true")) {
                    throw new RuntimeException("is-user-demo and is-use-testnet can't all true!");
                }
                if (StringUtils.isNotBlank(isUseDemo) && "true".equals(isUseDemo)) {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);
                }
                if (StringUtils.isNotBlank(isUseTestNet) && "true".equals(isUseTestNet)) {
                    exSpec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, true);
                }
                break;
            case OSL_GLOBAL_V2:
                exSpec = new GlobalExchange().getDefaultExchangeSpecification();
                isUseDemo = environment.getProperty(String.format("connect.exchange.%s.is_use_demo", exchange));
                if (StringUtils.isNotBlank(isUseDemo) && "true".equals(isUseDemo)) {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, true);
                } else {
                    exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, false);
                }
                break;
            default:
                throw new RuntimeException("Unsupported exchange: " + exchange);
        }

        if (StringUtils.isNotBlank(pwd)) {
            String apiKeyConfig = environment.getProperty(String.format("connect.exchange.%s.api_key", exchange));
            if (StringUtils.isBlank(apiKeyConfig)) {
                throw new RuntimeException("need api_key config!");
            }
            String secretKeyConfig = environment.getProperty(String.format("connect.exchange.%s.secret_key", exchange));
            if (StringUtils.isBlank(secretKeyConfig)) {
                throw new RuntimeException("need secret_key config!");
            }
            String passwordConfig = environment.getProperty(String.format("connect.exchange.%s.password", exchange));
            if (StringUtils.isBlank(passwordConfig)) {
                throw new RuntimeException("need password config!");
            }
            exSpec.setApiKey(apiKeyConfig);
            exSpec.setSecretKey(secretKeyConfig);
            exSpec.setPassword(passwordConfig);
        }

        synchronized (TRADING_MAP) {
            if (TRADING_MAP.containsKey(apiKey)) {
                return TRADING_MAP.get(apiKey);
            }
            Exchange exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exSpec);
            TRADING_MAP.put(apiKey, exchangeInstance);
            return exchangeInstance;
        }
    }

    @Override
    public Exchange getExchange(ExchangeCode exchange, boolean needAuth) {
        ExchangeSpecification exSpec = null;
        Class<? extends Exchange> exchangeClass = null;
        switch (exchange) {
            case BINANCE:
                exSpec = new BinanceExchange().getDefaultExchangeSpecification();
                boolean isUserSandBox = false;
                String useSandBox = environment.getProperty(String.format("connect.exchange.%s.use_sandbox", exchange));
                if (StringUtils.isNotBlank(useSandBox) && "true".equals(useSandBox)) {
                    isUserSandBox = true;
                }
                exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, isUserSandBox);

                if (needAuth) {
                    String apiKey = environment.getProperty(String.format("connect.exchange.%s.api_key", exchange));
                    if (StringUtils.isBlank(apiKey)) {
                        throw new RuntimeException("need api_key config!");
                    }
                    String secretKey = environment.getProperty(String.format("connect.exchange.%s.secret_key", exchange));
                    if (StringUtils.isBlank(secretKey)) {
                        throw new RuntimeException("need secret_key config!");
                    }
                    exSpec.setApiKey(apiKey);
                    exSpec.setSecretKey(secretKey);
                }
                break;
            case BITGET:
                exSpec = new BitgetExchange().getDefaultExchangeSpecification();
                isUserSandBox = false;
                useSandBox = environment.getProperty(String.format("connect.exchange.%s.use_sandbox", exchange));
                if (StringUtils.isNotBlank(useSandBox) && "true".equals(useSandBox)) {
                    isUserSandBox = true;
                }
                exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, isUserSandBox);

                if (needAuth) {
                    String apiKey = environment.getProperty(String.format("connect.exchange.%s.api_key", exchange));
                    if (StringUtils.isBlank(apiKey)) {
                        throw new RuntimeException("need api_key config!");
                    }
                    String secretKey = environment.getProperty(String.format("connect.exchange.%s.secret_key", exchange));
                    if (StringUtils.isBlank(secretKey)) {
                        throw new RuntimeException("need secret_key config!");
                    }
                    exSpec.setApiKey(apiKey);
                    exSpec.setSecretKey(secretKey);
                }
                break;
            case BYBIT:
                exSpec = new BybitExchange().getDefaultExchangeSpecification();
                isUserSandBox = false;
                useSandBox = environment.getProperty(String.format("connect.exchange.%s.use_sandbox", exchange));
                if (StringUtils.isNotBlank(useSandBox) && "true".equals(useSandBox)) {
                    isUserSandBox = true;
                }
                exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, isUserSandBox);

                String isUseTestNet = environment.getProperty(String.format("connect.exchange.%s.is_use_testnet", exchange));
                if (StringUtils.isNotBlank(isUseTestNet) && "true".equals(isUseTestNet)) {
                    exSpec.setExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET, true);
                }

                if (needAuth) {
                    String apiKey = environment.getProperty(String.format("connect.exchange.%s.api_key", exchange));
                    if (StringUtils.isBlank(apiKey)) {
                        throw new RuntimeException("need api_key config!");
                    }
                    String secretKey = environment.getProperty(String.format("connect.exchange.%s.secret_key", exchange));
                    if (StringUtils.isBlank(secretKey)) {
                        throw new RuntimeException("need secret_key config!");
                    }
                    exSpec.setApiKey(apiKey);
                    exSpec.setSecretKey(secretKey);
                }
                break;
            case OSL_GLOBAL:
                exSpec = new GlobalExchange().getDefaultExchangeSpecification();
                isUserSandBox = false;
                useSandBox = environment.getProperty(String.format("connect.exchange.%s.use_sandbox", exchange));
                if (StringUtils.isNotBlank(useSandBox) && "true".equals(useSandBox)) {
                    isUserSandBox = true;
                }
                exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, isUserSandBox);

                String apiUrl = environment.getProperty(String.format("connect.exchange.%s.api_url", exchange));
                if (StringUtils.isBlank(apiUrl)) {
                    apiUrl = "https://api-glb.osltest.com";
                }
                exSpec.setSslUri(apiUrl);
                String host = environment.getProperty(String.format("connect.exchange.%s.host", exchange));
                if (StringUtils.isBlank(host)) {
                    throw new RuntimeException("need host config!");
                }
                exSpec.setHost(host);

                if (needAuth) {
                    String apiKey = environment.getProperty(String.format("connect.exchange.%s.api_key", exchange));
                    if (StringUtils.isBlank(apiKey)) {
                        throw new RuntimeException("need api_key config!");
                    }
                    String secretKey = environment.getProperty(String.format("connect.exchange.%s.secret_key", exchange));
                    if (StringUtils.isBlank(secretKey)) {
                        throw new RuntimeException("need secret_key config!");
                    }
                    String password = environment.getProperty(String.format("connect.exchange.%s.password", exchange));
                    if (StringUtils.isBlank(password)) {
                        throw new RuntimeException("need password config!");
                    }
                    exSpec.setApiKey(apiKey);
                    exSpec.setSecretKey(secretKey);
                    exSpec.setPassword(password);
                }
                break;
            case OSL_GLOBAL_V2:
                exSpec = new GlobalExchange().getDefaultExchangeSpecification();
                isUserSandBox = false;
                useSandBox = environment.getProperty(String.format("connect.exchange.%s.use_sandbox", exchange));
                if (StringUtils.isNotBlank(useSandBox) && "true".equals(useSandBox)) {
                    isUserSandBox = true;
                }
                exSpec.setExchangeSpecificParametersItem(Exchange.USE_SANDBOX, isUserSandBox);

                apiUrl = environment.getProperty(String.format("connect.exchange.%s.api_url", exchange));
                if (StringUtils.isBlank(apiUrl)) {
                    apiUrl = "https://api-glb.osltest.com";
                }
                exSpec.setSslUri(apiUrl);
                host = environment.getProperty(String.format("connect.exchange.%s.host", exchange));
                if (StringUtils.isBlank(host)) {
                    throw new RuntimeException("need host config!");
                }
                exSpec.setHost(host);

                if (needAuth) {
                    String apiKey = environment.getProperty(String.format("connect.exchange.%s.api_key", exchange));
                    if (StringUtils.isBlank(apiKey)) {
                        throw new RuntimeException("need api_key config!");
                    }
                    String secretKey = environment.getProperty(String.format("connect.exchange.%s.secret_key", exchange));
                    if (StringUtils.isBlank(secretKey)) {
                        throw new RuntimeException("need secret_key config!");
                    }
                    String password = environment.getProperty(String.format("connect.exchange.%s.password", exchange));
                    if (StringUtils.isBlank(password)) {
                        throw new RuntimeException("need password config!");
                    }
                    exSpec.setApiKey(apiKey);
                    exSpec.setSecretKey(secretKey);
                    exSpec.setPassword(password);
                }
                break;
            default:
                throw new RuntimeException("Unsupported exchange: " + exchange);
        }

        if (exSpec != null) {
            if (needAuth) {
                synchronized (PRIVATE_EXCHANGE_MAP) {
                    if (PRIVATE_EXCHANGE_MAP.containsKey(exchange)) {
                        return PRIVATE_EXCHANGE_MAP.get(exchange);
                    }
                    Exchange exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exSpec);
                    PRIVATE_EXCHANGE_MAP.put(exchange, exchangeInstance);
                    return exchangeInstance;
                }
            } else {
                synchronized (PUBLIC_EXCHANGE_MAP) {
                    if (PUBLIC_EXCHANGE_MAP.containsKey(exchange)) {
                        return PUBLIC_EXCHANGE_MAP.get(exchange);
                    }
                    Exchange exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exSpec);
                    PUBLIC_EXCHANGE_MAP.put(exchange, exchangeInstance);
                    return exchangeInstance;
                }
            }
        } else if (exchangeClass != null) {
            synchronized (PUBLIC_EXCHANGE_MAP) {
                if (PUBLIC_EXCHANGE_MAP.containsKey(exchange)) {
                    return PUBLIC_EXCHANGE_MAP.get(exchange);
                }
                Exchange exchangeInstance = ExchangeFactory.INSTANCE.createExchange(exchangeClass);
                PUBLIC_EXCHANGE_MAP.put(exchange, exchangeInstance);
                return exchangeInstance;
            }
        }
        throw new RuntimeException("Unsupported exchange: " + exchange);
    }
}
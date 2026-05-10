package com.orderbook.core.config;

import com.orderbook.core.domain.ExchangeInfo;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "connect")
public class ExchangeConnectConfig {
    private Map<String, ExchangeInfo> exchange;

    public ExchangeInfo getExchangeInfo(String exchangeName) {
        if (exchange == null) {
            return null;
        }
        if (exchange.containsKey(exchangeName.toUpperCase())) {
            return exchange.get(exchangeName.toUpperCase());
        }
        return null;
    }
}
package com.orderbook.core.config;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.orderbook.core.domain.SymbolBo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class ApolloConfigRefresher {

    @Autowired
    StrategyProps configProps;

    @PostConstruct
    public void init() {
        Config config = ConfigService.getConfig("application"); // 替换为你的namespace
        config.addChangeListener((ConfigChangeEvent event) -> {
            Set<String> changedKeys = event.changedKeys();
            boolean hasSymbolChange = changedKeys.stream()
                    .anyMatch(key -> key.startsWith("strategy.config.symbols."));
            if (hasSymbolChange) {
                reloadSymbolConfig();
            }
        });
    }

    private void reloadSymbolConfig() {
        Config apolloConfig = ConfigService.getConfig("application");
        Map<String, SymbolBo> result = new HashMap<>();

        for (String key : apolloConfig.getPropertyNames()) {
            if (!key.startsWith("strategy.config.symbols.")) continue;

            // 分割key
            String[] parts = key.split("\\.");
            if (parts.length < 5) continue; // 最少要有exchange index field三个部分

            String symbol = parts[3]; // BTCUSDT ETHUSDT等
            String field = parts[4];  // symbolId baseTokenId ...

            result.putIfAbsent(symbol, new SymbolBo());
            SymbolBo bo = result.get(symbol);
            String value = apolloConfig.getProperty(key, "");

            switch (field) {
                case "open":
                    bo.setOpen(Boolean.parseBoolean(value));
                    break;
                case "updateIntervalMs":
                    bo.setUpdateIntervalMs(Long.parseLong(value));
                    break;
                case "baseTokenId":
                    bo.setBaseTokenId(value);
                    break;
                case "quoteTokenId":
                    bo.setQuoteTokenId(value);
                    break;
                case "platformTakerRate":
                    bo.setPlatformTakerRate(new BigDecimal(value));
                    break;
                case "tickSize":
                    bo.setTickSize(new BigDecimal(value));
                    break;
                case "stepSize":
                    bo.setStepSize(new BigDecimal(value));
                    break;
                case "minSize":
                    bo.setMinSize(new BigDecimal(value));
                    break;
                case "minRate":
                    bo.setMinRate(new BigDecimal(value));
                    break;
                case "maxRate":
                    bo.setMaxRate(new BigDecimal(value));
                    break;
                case "maxDelegateCount":
                    bo.setMaxDelegateCount(new BigDecimal(value));
                    break;
                case "accountId":
                    bo.setAccountId(value);
                    break;
                case "apiKey":
                    bo.setApiKey(value);
                    break;
                case "secretKey":
                    bo.setSecretKey(value);
                    break;
                case "password":
                    bo.setPassword(value);
                    break;
            }
        }

        for (Map.Entry<String, SymbolBo> entry : result.entrySet()) {
            configProps.setSymbol(entry.getValue());
        }
    }
}
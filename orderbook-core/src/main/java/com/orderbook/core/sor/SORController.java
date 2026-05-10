package com.orderbook.core.sor;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.config.ApolloConfig;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sor")
public class SORController {

    private final SOREngine sorEngine;
    private final RoutingTable routingTable;
    private final ApolloConfig apolloConfig;

    public SORController(SOREngine sorEngine, RoutingTable routingTable, ApolloConfig apolloConfig) {
        this.sorEngine = sorEngine;
        this.routingTable = routingTable;
        this.apolloConfig = apolloConfig;
    }

    @GetMapping("/stats")
    public List<ExchangeStats> getStats() {
        return routingTable.getAllStats();
    }

    @GetMapping("/rank/{symbol}")
    public List<ExchangeCode> getRank(@PathVariable String symbol) {
        return sorEngine.rankExchanges(symbol);
    }

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", apolloConfig.getSOREnabled());
        config.put("strategy", apolloConfig.getSORStrategy());
        config.put("primaryExchange", apolloConfig.getSORPrimaryExchange());
        config.put("fallbackExchanges", apolloConfig.getSORFallbackExchanges());
        return config;
    }
}

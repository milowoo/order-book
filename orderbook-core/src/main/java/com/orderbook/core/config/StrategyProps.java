package com.orderbook.core.config;

import com.orderbook.core.domain.SymbolBo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

@Slf4j
@Data
@ConfigurationProperties(prefix = "strategy.config")
public class StrategyProps {

    private Map<String, SymbolBo> symbols = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("StrategyProps loaded symbols: {}", symbols.keySet());
    }

    public void setSymbol(SymbolBo symbol) {
        symbols.put(symbol.getSymbolId(), symbol);
    }

    public SymbolBo findSymbol(String symbol) {
        SymbolBo exactMatch = symbols.get(symbol);
        if (exactMatch != null) {
            return exactMatch;
        }

        return symbols.values().stream()
                .filter(bo -> {
                    String base = bo.getBaseTokenId();
                    String quote = bo.getQuoteTokenId();
                    return symbol.startsWith(base) && symbol.indexOf(quote, base.length()) > 0;
                })
                .findFirst()
                .orElse(null);
    }

    public Set<String> allSymbols() {
        return symbols.keySet();
    }

    public List<SymbolBo> getActiveSymbols() {
        return symbols.values().stream()
                .filter(SymbolBo::isOpen)
                .collect(Collectors.toList());
    }

    public SymbolBo mapping(String symbol) {
        return symbols.values().stream().filter(
                (SymbolBo item) -> item.getSymbol().equalsIgnoreCase(symbol)
        ).findFirst().get();
    }
}
package com.orderbook.core.store;

import com.orderbook.core.config.StrategyProps;
import com.orderbook.core.domain.SymbolBo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class SymbolStore {

    @Autowired
    StrategyProps config;

    public SymbolBo findSymbolById(String symbolId) {
        if (symbolId == null) {
            return null;
        }
        return config.findSymbol(symbolId);
    }

    public List<SymbolBo> getActiveSymbols() {
        return config.getActiveSymbols();
    }

    public SymbolBo mappingBySymbol(String symbol) {
        return config.mapping(symbol);
    }
}
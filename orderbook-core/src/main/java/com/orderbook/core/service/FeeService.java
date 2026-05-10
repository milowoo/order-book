package com.orderbook.core.service;

import com.orderbook.cmd.ExchangeCode;
import com.orderbook.core.entity.FeeConfigEntity;
import com.orderbook.core.mapper.FeeConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fee configuration service loaded from fee_config table.
 * Provides taker/maker rates per exchange and break-even spread calculation.
 */
@Slf4j
@Service
public class FeeService {

    private final FeeConfigMapper feeConfigMapper;

    // Cache: exchange -> (symbol -> FeeConfigEntity)
    // symbol can be "*" for wildcard (default for all symbols on that exchange)
    private final Map<String, Map<String, FeeConfigEntity>> configCache = new ConcurrentHashMap<>();

    public FeeService(FeeConfigMapper feeConfigMapper) {
        this.feeConfigMapper = feeConfigMapper;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * Reload fee configurations from database.
     */
    public void reload() {
        try {
            List<FeeConfigEntity> configs = feeConfigMapper.selectList(null);
            configCache.clear();
            for (FeeConfigEntity cfg : configs) {
                String exchange = cfg.getExchange().toUpperCase();
                configCache.computeIfAbsent(exchange, k -> new ConcurrentHashMap<>())
                        .put(cfg.getSymbol(), cfg);
            }
            log.info("[FeeService] Loaded {} fee configs for {} exchanges", configs.size(), configCache.size());
        } catch (Exception e) {
            log.warn("[FeeService] Failed to load fee configs from DB: {}", e.getMessage());
        }
    }

    /**
     * Get taker fee rate for an exchange and symbol.
     * Falls back to wildcard "*" config if symbol-specific not found.
     * Falls back to 0.001 (0.1%) if no config found.
     */
    public BigDecimal getTakerRate(ExchangeCode exchange, String symbol) {
        return getRate(exchange, symbol, FeeConfigEntity::getTakerRate, new BigDecimal("0.001"));
    }

    /**
     * Get maker fee rate for an exchange and symbol.
     */
    public BigDecimal getMakerRate(ExchangeCode exchange, String symbol) {
        return getRate(exchange, symbol, FeeConfigEntity::getMakerRate, new BigDecimal("0.001"));
    }

    /**
     * Calculate the minimum break-even spread in price terms for round-trip taker fees.
     * breakEvenSpread = refPrice * 2 * takerRate
     * This is the total spread needed to cover taker fees on both buy and sell sides.
     */
    public BigDecimal calculateBreakEvenSpread(ExchangeCode exchange, String symbol, BigDecimal refPrice) {
        if (refPrice == null || refPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal takerRate = getTakerRate(exchange, symbol);
        return refPrice.multiply(BigDecimal.valueOf(2))
                .multiply(takerRate)
                .setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Estimate the fee for a given notional amount.
     * fee = notional * takerRate
     */
    public BigDecimal estimateFee(ExchangeCode exchange, String symbol, BigDecimal notional) {
        if (notional == null || notional.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal takerRate = getTakerRate(exchange, symbol);
        return notional.multiply(takerRate).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Get taker rates for all exchanges for a given symbol.
     */
    public Map<ExchangeCode, BigDecimal> getAllTakerRates(String symbol) {
        Map<ExchangeCode, BigDecimal> rates = new LinkedHashMap<>();
        for (ExchangeCode exchange : ExchangeCode.values()) {
            rates.put(exchange, getTakerRate(exchange, symbol));
        }
        return rates;
    }

    // ---- Private helpers ----

    private BigDecimal getRate(ExchangeCode exchange, String symbol,
                               java.util.function.Function<FeeConfigEntity, BigDecimal> rateExtractor,
                               BigDecimal defaultRate) {
        String exchangeName = exchange.name().toUpperCase();
        Map<String, FeeConfigEntity> exchangeConfigs = configCache.get(exchangeName);
        if (exchangeConfigs == null) {
            return defaultRate;
        }

        // Try symbol-specific config first
        if (symbol != null) {
            FeeConfigEntity specific = exchangeConfigs.get(symbol);
            if (specific != null) {
                return rateExtractor.apply(specific);
            }
        }

        // Fall back to wildcard
        FeeConfigEntity wildcard = exchangeConfigs.get("*");
        if (wildcard != null) {
            return rateExtractor.apply(wildcard);
        }

        return defaultRate;
    }
}
